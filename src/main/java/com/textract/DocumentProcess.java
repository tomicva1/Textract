package com.textract;

import com.amazonaws.services.textract.model.AnalyzeDocumentResult;
import com.amazonaws.services.textract.model.Block;
import com.amazonaws.services.textract.model.DetectDocumentTextResult;
import com.amazonaws.services.textract.model.Document;
import com.google.api.gax.longrunning.OperationFuture;
import com.google.cloud.storage.*;
import com.google.cloud.vision.v1.*;
import com.google.protobuf.util.JsonFormat;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DocumentProcess {

    private String inputPath = "D:\\Temp\\Input";
    private String outputPath = "D:\\Temp\\Output";
    private Document myDoc;
    private String name;
    private List<DetectDocumentTextResult> result;

    private static final HotFolderCaller hotFolderCaller = new HotFolderCaller();

    public DocumentProcess(){}

    public void processDocuments(String name) throws IOException {
        System.out.println("Document name is " + name);
        result = new ArrayList<DetectDocumentTextResult>();

        this.name = name;
        getAllFilesInBytes();
        //CreateNewTXTDocument();
        XWPFDocument document = createNewDocxDocument();

        for(DetectDocumentTextResult r : result) {
            for (int i = 0; i < r.getBlocks().size(); i++) {
                Block block = r.getBlocks().get(i);
                String type = block.getBlockType();
                if (type.equals("LINE")) {
                    String text = block.getText();
                    //WriteToTXTFile(text);
                    writeToDocxFile(text, document, false);
                }
            }
        }
    }

    public void processDocumentsGoogle(String name) throws IOException, JSONException {

        this.name = name;

        File file = new File(inputPath);
        System.out.println("Processing documents: " + file.listFiles().length);
        for(int f = 0; f < file.listFiles().length; f++) {
            System.out.println(file.getPath() + "\\" + file.listFiles()[f].getName());
            System.out.println("Absolut path in google: " + file.listFiles()[f].getAbsolutePath());

            String filename = file.listFiles()[f].getName();

            String bucketName = "service_bucket_ocr";
            String gcsSourcePath = "gs://service_bucket_ocr/inputs/";
            String gcsDestinationPath = "gs://service_bucket_ocr/outputs/" + name + "_";

            // Initialize client that will be used to send requests. This client only needs to be created
            // once, and can be reused for multiple requests. After completing all of your requests, call
            // the "close" method on the client to safely clean up any remaining background resources.
            try (ImageAnnotatorClient client = ImageAnnotatorClient.create()) {
                List<AsyncAnnotateFileRequest> requests = new ArrayList<>();

                //import document
                Storage storage = StorageOptions.getDefaultInstance().getService();
                BlobId blobId = BlobId.of(bucketName, "inputs/"+ filename);
                BlobInfo blobInfo = BlobInfo.newBuilder(blobId).build();
                storage.create(blobInfo, Files.readAllBytes(Paths.get(file.listFiles()[f].getAbsolutePath())));

                // Set the GCS source path for the remote file.
                GcsSource gcsSource = GcsSource.newBuilder().setUri(gcsSourcePath + filename).build();

                // Create the configuration with the specified MIME (Multipurpose Internet Mail Extensions)
                // types
                InputConfig inputConfig =
                        InputConfig.newBuilder()
                                .setMimeType(
                                        "application/pdf") // Supported MimeTypes: "application/pdf", "image/tiff"
                                .setGcsSource(gcsSource)
                                .build();

                // Set the GCS destination path for where to save the results.
                GcsDestination gcsDestination =
                        GcsDestination.newBuilder().setUri(gcsDestinationPath).build();

                // Create the configuration for the System.output with the batch size.
                // The batch size sets how many pages should be grouped into each json System.output file.
                OutputConfig outputConfig =
                        OutputConfig.newBuilder().setBatchSize(2).setGcsDestination(gcsDestination).build();

                // Select the Feature required by the vision API
                Feature feature = Feature.newBuilder().setType(Feature.Type.DOCUMENT_TEXT_DETECTION).build();

                // Build the OCR request
                AsyncAnnotateFileRequest request =
                        AsyncAnnotateFileRequest.newBuilder()
                                .addFeatures(feature)
                                .setInputConfig(inputConfig)
                                .setOutputConfig(outputConfig)
                                .build();

                requests.add(request);

                // Perform the OCR request
                OperationFuture<AsyncBatchAnnotateFilesResponse, OperationMetadata> response =
                        client.asyncBatchAnnotateFilesAsync(requests);

                System.out.println("Waiting for the operation to finish.");

                // Wait for the request to finish. (The result is not used, since the API saves the result to
                // the specified location on GCS.)
                List<AsyncAnnotateFileResponse> result =
                        response.get(180, TimeUnit.SECONDS).getResponsesList();

                // Get the destination location from the gcsDestinationPath
                Pattern pattern = Pattern.compile("gs://([^/]+)/(.+)");
                Matcher matcher = pattern.matcher(gcsDestinationPath);

                if (matcher.find()) {
                    String bucketName2 = matcher.group(1);
                    String prefix = matcher.group(2);

                    // Get the list of objects with the given prefix from the GCS bucket
                    Bucket bucket = storage.get(bucketName2);
                    com.google.api.gax.paging.Page<Blob> pageList = bucket.list(Storage.BlobListOption.prefix(prefix));

                    Blob firstOutputFile = null;

                    // List objects with the given prefix.
                    System.out.println("Output files:");
                    for (Blob blob : pageList.iterateAll()) {
                        System.out.println(blob.getName());

                        // Process the first System.output file from GCS.
                        // Since we specified batch size = 2, the first response contains
                        // the first two pages of the input file.
                        if (firstOutputFile == null) {
                            firstOutputFile = blob;
                        }
                    }

                    // Get the contents of the file and convert the JSON contents to an AnnotateFileResponse
                    // object. If the Blob is small read all its content in one request
                    // (Note: the file is a .json file)
                    // Storage guide: https://cloud.google.com/storage/docs/downloading-objects
                    String jsonContents = new String(firstOutputFile.getContent());
                    System.out.println("MOJE: " + jsonContents);

                    JSONObject obj = new JSONObject(jsonContents);

                    XWPFDocument document = new XWPFDocument();

                    JSONArray responses = obj.getJSONArray("responses");
                    for(int i = 0; i < responses.length(); i++){
                        JSONArray pages = responses.getJSONObject(i).getJSONObject("fullTextAnnotation").getJSONArray("pages");
                        for(int j = 0; j < pages.length(); j++) {
                            JSONArray blocks = pages.getJSONObject(j).getJSONArray("blocks");
                            for(int k = 0; k < blocks.length(); k++) {
                                if (blocks.getJSONObject(k).getString("blockType").equals("TEXT")) {
                                    JSONArray paragraphs = blocks.getJSONObject(k).getJSONArray("paragraphs");
                                    for (int l = 0; l < paragraphs.length(); l++) {
                                        String paragraph = "";
                                        JSONArray words = paragraphs.getJSONObject(l).getJSONArray("words");
                                        for (int m = 0; m < words.length(); m++) {
                                            JSONArray symbols = words.getJSONObject(m).getJSONArray("symbols");
                                            for (int n = 0; n < symbols.length(); n++){
                                                JSONObject symbol = symbols.getJSONObject(n);
                                                paragraph = paragraph + symbol.getString("text");
                                                if(symbol.has("property") && symbol.getJSONObject("property").has("detectedBreak") && symbol.getJSONObject("property").getJSONObject("detectedBreak").has("type")){
                                                    if(symbol.getJSONObject("property").getJSONObject("detectedBreak").getString("type").equals("SPACE"))
                                                        paragraph = paragraph + " ";
                                                    else if (symbol.getJSONObject("property").getJSONObject("detectedBreak").getString("type").equals("LINE_BREAK"))
                                                        paragraph = paragraph + " ";
                                                }
                                            }
                                        }
                                        System.out.println(paragraph);
                                        writeToDocxFile(paragraph, document, true);
                                    }
                                }
                            }
                        }
                    }

                    AnnotateFileResponse.Builder builder = AnnotateFileResponse.newBuilder();
                    JsonFormat.parser().merge(jsonContents, builder);

                    // Build the AnnotateFileResponse object
                    AnnotateFileResponse annotateFileResponse = builder.build();

                    // Parse through the object to get the actual response for the first page of the input file.
                    AnnotateImageResponse annotateImageResponse = annotateFileResponse.getResponses(0);

                    // Here we print the full text from the first page.
                    // The response contains more information:
                    // annotation/pages/blocks/paragraphs/words/symbols
                    // including confidence score and bounding boxes
                    System.out.format("%nText: %s%n", annotateImageResponse.getFullTextAnnotation().getText());
                } else {
                    System.out.println("No MATCH");
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            } catch (ExecutionException e) {
                e.printStackTrace();
            } catch (TimeoutException e) {
                e.printStackTrace();
            }

        }
    }

    private void getAllFilesInBytes(){
        File file = new File(inputPath);
        TextractCaller textractCaller = new TextractCaller();
        textractCaller.startTextract();

        try {
            System.out.println("Processing documents: " + file.listFiles().length);
            for(int f = 0; f < file.list().length; f++) {
                System.out.println(file.getPath() + "\\" + file.list()[f]);
                InputStream sourceStream = new FileInputStream(file.getPath() + "\\" + file.list()[f]);

                byte[] bytes = sourceStream.readAllBytes();
                sourceStream.close();
                Document doc = new com.amazonaws.services.textract.model.Document().withBytes(ByteBuffer.wrap(bytes));
                result.add(textractCaller.analyzeDocument(doc));
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
        textractCaller.closeTextract();
    }

    private void createNewTXTDocument(){
        File newFile = new File(outputPath + "\\" + name + ".txt");
        try {
            if(newFile.createNewFile()){
                System.out.println("Created " + newFile.getAbsolutePath());
            }
            else {
                System.out.println("File already exists.");
            }
        } catch (IOException e) {
            System.out.println("An error occurred.");
            e.printStackTrace();
        }
    }

    private XWPFDocument createNewDocxDocument(){
        //Blank Document
        XWPFDocument document = new XWPFDocument();
        return document;
    }

    private void writeToTXTFile(String text){
        try {
            FileWriter myWriter = new FileWriter(outputPath + "\\" + name + ".txt", true);
            BufferedWriter bw = new BufferedWriter(myWriter);
            bw.write(text);
            bw.newLine();
            bw.close();
            System.out.println("Successfully wrote to the file.");
        } catch (IOException e) {
            System.out.println("An error occurred.");
            e.printStackTrace();
        }
    }

    private void writeToDocxFile(String text, XWPFDocument document, boolean isGoogle) throws IOException{
        //Write the Document in file system
        FileOutputStream out = null;
        try {
            if(isGoogle)
                out = new FileOutputStream(outputPath + "\\" + name + "_Google.docx");
            else
                out = new FileOutputStream(outputPath + "\\" + name + ".docx");
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        //create Paragraph
        XWPFParagraph paragraph = document.createParagraph();
        XWPFRun run = paragraph.createRun();
        run.setText(text);
        document.write(out);
        //Close document
        out.close();
        System.out.println(outputPath + "\\" + name + ".docx" + " written successfully");
    }

    public void deleteFiles(){
        File file = new File(inputPath);
        while(file.list().length > 0) {
            System.out.println("Deleting " + file.getPath() + "\\" + file.list()[0]);
            new File(file.getPath() + "\\" + file.list()[0]).delete();
        }
    }
}

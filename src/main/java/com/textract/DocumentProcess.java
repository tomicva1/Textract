package com.textract;

import com.amazonaws.services.textract.model.AnalyzeDocumentResult;
import com.amazonaws.services.textract.model.Block;
import com.amazonaws.services.textract.model.DetectDocumentTextResult;
import com.amazonaws.services.textract.model.Document;
import com.google.api.gax.longrunning.OperationFuture;
import com.google.cloud.storage.*;
import com.google.cloud.vision.v1.*;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;
import com.google.protobuf.ByteString;
import com.google.protobuf.util.JsonFormat;
import com.microsoft.azure.cognitiveservices.vision.computervision.ComputerVision;
import com.microsoft.azure.cognitiveservices.vision.computervision.ComputerVisionClient;
import com.microsoft.azure.cognitiveservices.vision.computervision.ComputerVisionManager;
import com.microsoft.azure.cognitiveservices.vision.computervision.implementation.ComputerVisionImpl;
import com.microsoft.azure.cognitiveservices.vision.computervision.models.*;
import com.microsoft.rest.ServiceResponseWithHeaders;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DocumentProcess {

    private String inputPath = "D:\\Temp\\Input";
    private String outputPath = "D:\\Temp\\Output";
    private String name;
    private List<DetectDocumentTextResult> result;

    //Azure
    static String subscriptionKey = "4fbf2bef8a394836bfb874a716dc5a70";
    static String endpoint = "https://azureocr1.cognitiveservices.azure.com/";

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

    public void processDocumentGoogle(String name) throws IOException, JSONException {
        this.name = name;
        XWPFDocument document = new XWPFDocument();

        File file = new File(inputPath);
        System.out.println("Processing documents: " + file.listFiles().length);
        for (int f = 0; f < file.listFiles().length; f++) {
            System.out.println(file.getPath() + "\\" + file.listFiles()[f].getName());
            System.out.println("Absolut path in google: " + file.listFiles()[f].getAbsolutePath());

            String filename = file.listFiles()[f].getName();
            if(filename.endsWith(".pdf") || filename.endsWith(".PDF")) {
                processDocumentPDFGoogle(file.listFiles()[f], document);
            }
            else{
                List<AnnotateImageRequest> requests = new ArrayList<>();

                ByteString imgBytes = ByteString.readFrom(new FileInputStream(file.listFiles()[f].getAbsolutePath()));

                Image img = Image.newBuilder().setContent(imgBytes).build();
                Feature feat = Feature.newBuilder().setType(Feature.Type.TEXT_DETECTION).build();
                AnnotateImageRequest request =
                        AnnotateImageRequest.newBuilder().addFeatures(feat).setImage(img).build();
                requests.add(request);

                // Initialize client that will be used to send requests. This client only needs to be created
                // once, and can be reused for multiple requests. After completing all of your requests, call
                // the "close" method on the client to safely clean up any remaining background resources.
                try (ImageAnnotatorClient client = ImageAnnotatorClient.create()) {
                    BatchAnnotateImagesResponse response = client.batchAnnotateImages(requests);

                    Gson gson = new Gson();
                    String str = gson.toJson(response);

                    JsonObject obj1 = JsonParser.parseString(str).getAsJsonObject();
                    JSONObject obj = new JSONObject(obj1.toString());

                    JSONArray responses = obj.getJSONArray("responses_");
                    for (int i = 0; i < responses.length(); i++) {
                        System.out.println("responses");
                        JSONArray pages = responses.getJSONObject(i).getJSONObject("fullTextAnnotation_").getJSONArray("pages_");
                        for (int j = 0; j < pages.length(); j++) {
                            System.out.println("pages");
                            JSONArray blocks = pages.getJSONObject(j).getJSONArray("blocks_");
                            for (int k = 0; k < blocks.length(); k++) {
                                if (blocks.getJSONObject(k).getString("blockType_").equals("1")) {
                                    System.out.println("blocktype");
                                    JSONArray paragraphs = blocks.getJSONObject(k).getJSONArray("paragraphs_");
                                    for (int l = 0; l < paragraphs.length(); l++) {
                                        System.out.println("paragraphs");
                                        String paragraph = "";
                                        JSONArray words = paragraphs.getJSONObject(l).getJSONArray("words_");
                                        for (int m = 0; m < words.length(); m++) {
                                            JSONArray symbols = words.getJSONObject(m).getJSONArray("symbols_");
                                            for (int n = 0; n < symbols.length(); n++) {
                                                JSONObject symbol = symbols.getJSONObject(n);
                                                paragraph = paragraph + symbol.getString("text_");
                                                if (symbol.has("property_") && symbol.getJSONObject("property_").has("detectedBreak_") && symbol.getJSONObject("property_").getJSONObject("detectedBreak_").has("type_")) {
                                                    if (symbol.getJSONObject("property_").getJSONObject("detectedBreak_").getString("type_").equals("1"))
                                                        paragraph = paragraph + " ";
                                                    else if (symbol.getJSONObject("property_").getJSONObject("detectedBreak_").getString("type_").equals("3"))
                                                        paragraph = paragraph + " ";
                                                    else if (symbol.getJSONObject("property_").getJSONObject("detectedBreak_").getString("type_").equals("5"))
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
                }
            }
        }
    }

    public void processDocumentPDFGoogle(File file, XWPFDocument document) throws IOException, JSONException {

        String filename = file.getName();
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
            storage.create(blobInfo, Files.readAllBytes(Paths.get(file.getAbsolutePath())));

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
                                                else if (symbol.getJSONObject("property").getJSONObject("detectedBreak").getString("type").equals("EOL_SURE_SPACE"))
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

    public void processDocumentsAzure(String name) throws IOException, JSONException {
        this.name = name;

        XWPFDocument document = new XWPFDocument();

        File file = new File(inputPath);
        System.out.println("Processing documents: " + file.listFiles().length);
        for(int f = 0; f < file.listFiles().length; f++) {
            // Create an authenticated Computer Vision client.
            ComputerVisionClient compVisClient = Authenticate(subscriptionKey, endpoint);

            // Read from remote image
            ReadFromUrl(compVisClient, file.listFiles()[f], document);
            compVisClient.restClient().close();
        }
    }

    public static ComputerVisionClient Authenticate(String subscriptionKey, String endpoint){
        return ComputerVisionManager.authenticate(subscriptionKey).withEndpoint(endpoint);
    }

    /**
     * OCR with READ : Performs a Read Operation
     * @param client instantiated vision client
     */
    private void ReadFromUrl(ComputerVisionClient client, File file, XWPFDocument document) {
        System.out.println("-----------------------------------------------");

        String remoteTextImageURL = file.getAbsolutePath();
        System.out.println("Read with URL: " + remoteTextImageURL);

        try {
            // Cast Computer Vision to its implementation to expose the required methods
            ComputerVisionImpl vision = (ComputerVisionImpl) client.computerVision();

            // Read in remote image and response header
            File imgPath = new File(remoteTextImageURL);

            // get DataBufferBytes from Raster
            InputStream sourceStream = new FileInputStream(file.getAbsoluteFile());

            byte[] data = sourceStream.readAllBytes();

            /*BufferedImage originalImage= ImageIO.read(imgPath);
            ByteArrayOutputStream baos=new ByteArrayOutputStream();
            ImageIO.write(originalImage, "png", baos );
            byte[] data=baos.toByteArray();*/

            ReadInStreamHeaders responseHeader = vision.readInStreamWithServiceResponseAsync(data, null)
                    .toBlocking()
                    .single()
                    .headers();

            ServiceResponseWithHeaders<Void, ReadInStreamHeaders> responseHeader2 = vision.readInStreamWithServiceResponseAsync(data, null)
                    .toBlocking()
                    .single();


            System.out.println(responseHeader2.toString());
            System.out.println(responseHeader2.response().body().string());
            //System.out.println(responseHeader.toBlocking().single().headers());
            // Extract the operation Id from the operationLocation header
            String operationLocation = responseHeader.operationLocation();
            System.out.println("Operation Location:" + operationLocation);

            getAndPrintReadResult(vision, operationLocation, document);

        } catch (Exception e) {
            System.out.println(e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Extracts the OperationId from a Operation-Location returned by the POST Read operation
     * @param operationLocation
     * @return operationId
     */
    private String extractOperationIdFromOpLocation(String operationLocation) {
        if (operationLocation != null && !operationLocation.isEmpty()) {
            String[] splits = operationLocation.split("/");

            if (splits != null && splits.length > 0) {
                return splits[splits.length - 1];
            }
        }
        throw new IllegalStateException("Something went wrong: Couldn't extract the operation id from the operation location");
    }

    /**
     * Polls for Read result and prints results to console
     * @param vision Computer Vision instance
     * @return operationLocation returned in the POST Read response header
     */
    private void getAndPrintReadResult(ComputerVision vision, String operationLocation, XWPFDocument document) throws InterruptedException, IOException {
        System.out.println("Polling for Read results ...");

        // Extract OperationId from Operation Location
        String operationId = extractOperationIdFromOpLocation(operationLocation);

        boolean pollForResult = true;
        ReadOperationResult readResults = null;

        while (pollForResult) {
            // Poll for result every second
            Thread.sleep(1000);
            readResults = vision.getReadResult(UUID.fromString(operationId));

            // The results will no longer be null when the service has finished processing the request.
            if (readResults != null) {
                // Get request status
                OperationStatusCodes status = readResults.status();

                if (status == OperationStatusCodes.FAILED || status == OperationStatusCodes.SUCCEEDED) {
                    pollForResult = false;
                }
            }
        }

        System.out.println(readResults.analyzeResult().readResults().get(0).lines().size());
        // Print read results, page per page
        for (ReadResult pageResult : readResults.analyzeResult().readResults()) {
            System.out.println("");
            System.out.println("Printing Read results for page " + pageResult.page());

            for (Line line : pageResult.lines()) {
                writeToDocxFile(line.text(), document, false);
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
        int counter = file.list().length;
        while(counter > 0) {
            System.out.println("Deleting " + file.getPath() + "\\" + file.list()[0]);
            new File(file.getPath() + "\\" + file.list()[0]).delete();
            counter--;
        }

        if(file.list().length > 0){
            System.out.println("NOT ALL FILES WERE DELETED IN INPUT FOLDER!");
        }
    }
}

package com.textract;

import com.amazonaws.services.textract.model.AnalyzeDocumentResult;
import com.amazonaws.services.textract.model.Block;
import com.amazonaws.services.textract.model.DetectDocumentTextResult;
import com.amazonaws.services.textract.model.Document;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;

import java.io.*;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

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
                    writeToDocxFile(text, document);
                }
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
            deleteFiles();

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

    private void writeToDocxFile(String text, XWPFDocument document) throws IOException{
        //Write the Document in file system
        FileOutputStream out = null;
        try {
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

    private void deleteFiles(){
        File file = new File(inputPath);
        while(file.list().length > 0) {
            System.out.println("Deleting " + file.getPath() + "\\" + file.list()[0]);
            new File(file.getPath() + "\\" + file.list()[0]).delete();
        }
    }
}

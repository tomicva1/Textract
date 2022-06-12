import com.amazonaws.services.iotevents.model.Input;
import org.apache.pdfbox.contentstream.PDContentStream;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDStream;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.textract.TextractClient;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

@TestInstance(TestInstance.Lifecycle.PER_METHOD)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class AmazonTextractServiceIntegrationTest {

    private String outputPath = "D:\\Temp\\Output";
    private String name = "testGoogle";
    private static TextractClient textractClient;
    private static Region region;
    private static String sourceDoc = "";
    private static String bucketName = "";
    private static String docName = "";

    @BeforeAll
    public static void setUp() throws IOException {

        // Run tests on Real AWS Resources
        region = Region.EU_CENTRAL_1;
        textractClient = TextractClient.builder()
                .region(region)
                .build();

        try (InputStream input = AmazonTextractServiceIntegrationTest.class.getClassLoader().getResourceAsStream("config.properties")) {

            Properties prop = new Properties();

            if (input == null) {
                System.out.println("Sorry, unable to find config.properties");
                return;
            }

            //load a properties file from class path, inside static method
            prop.load(input);

            // Populate the data members required for all tests
            sourceDoc = prop.getProperty("sourceDoc");
            bucketName = prop.getProperty("bucketName");
            docName = prop.getProperty("docName");


        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    @Test
    @Order(1)
    public void whenInitializingTextractService_thenNotNull() {
        assertNotNull(textractClient);
        System.out.println(textractClient + "Test 1 passed");
    }

    @Test
    public void myTest1() throws IOException, JSONException {
        //File file = new File("D:/Downloads/outputs_InputEnglish_output-1-to-1.json");
        String jsonContents = new String(Files.readAllBytes(Paths.get("D:/Downloads/outputs_InputEnglish_output-1-to-1.json")));
        //String jsonContents = new String(firstOutputFile.getContent());

        //String jsonContents = "";
        //System.out.println("MOJE: " + jsonContents);

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
                            writeToDocxFile(paragraph, document);
                        }
                    }
                }
            }
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

    private void writeToPDFFile(List<String> texts) throws IOException {
        //InputStream is;
        StringBuilder finalString = new StringBuilder();
        for(String text : texts){
            finalString.append(text);
        }

        PDDocument pdfdoc = new PDDocument().load(finalString.toString().getBytes());

        //path where the PDF file will be store
        pdfdoc.save(outputPath + "\\" + name + ".pdf");
        //prints the message if the PDF is created successfully
        System.out.println("PDF created");
        //closes the document
        pdfdoc.close();
    }

   /* @Test
    @Order(2)
    public void AnalyzeDocument() {

        AnalyzeDocument.analyzeDoc(textractClient, sourceDoc);
        System.out.println("Test 2 passed");
    }

    @Test
    @Order(3)
    public void DetectDocumentText() {

        DetectDocumentText.detectDocText(textractClient, sourceDoc);
        System.out.println("Test 3 passed");
    }

    @Test
    @Order(4)
    public void DetectDocumentTextS3() {

        DetectDocumentTextS3.detectDocTextS3(textractClient, bucketName, docName);
        System.out.println("Test 4 passed");
    }

    @Test
    @Order(5)
    public void StartDocumentAnalysis() {

        StartDocumentAnalysis.startDocAnalysisS3(textractClient, bucketName, docName);
        System.out.println("Test 5 passed");
    }

    @Test
    @Order(6)
    public void GetResult() {

        String result = StartDocumentAnalysis.getJobResults(textractClient,"126cb142443274e4cc48fb7b7f6ecdd1b99cfdda2b415ddae69ac35f2ad318f8");
        System.out.println(result);
        System.out.println("Test 5 passed");
    }*/
}
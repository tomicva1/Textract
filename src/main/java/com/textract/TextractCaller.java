package com.textract;

import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.textract.TextractClient;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.client.builder.AwsClientBuilder.EndpointConfiguration;
import com.amazonaws.services.textract.AmazonTextract;
import com.amazonaws.services.textract.AmazonTextractClientBuilder;
import com.amazonaws.services.textract.model.DetectDocumentTextRequest;
import com.amazonaws.services.textract.model.DetectDocumentTextResult;
import com.amazonaws.services.textract.model.Document;

public class TextractCaller {

    private Region region = Region.EU_CENTRAL_1;
    private TextractClient textractClient;
    private AmazonS3 s3client;
    private EndpointConfiguration endpoint;
    private AmazonTextract client;

    TextractCaller(){}

    public void startTextract(){
        /*textractClient = TextractClient.builder()
                .region(region)
                .build();*/
        //s3client = AmazonS3ClientBuilder.standard().withEndpointConfiguration(new EndpointConfiguration("https://s3.amazonaws.com","eu-central-1")).build();
        endpoint = new EndpointConfiguration("https://textract.eu-central-1.amazonaws.com", "eu-central-1");
        client = AmazonTextractClientBuilder.standard().withEndpointConfiguration(endpoint).build();

    }

    public void closeTextract(){
        //textractClient.close();
        client.shutdown();
    }

    public DetectDocumentTextResult analyzeDocument(Document myDoc){
        DetectDocumentTextRequest request = new DetectDocumentTextRequest().withDocument(myDoc);
        DetectDocumentTextResult result = client.detectDocumentText(request);

        //System.out.println(result);

        return result;
        /*List<FeatureType> featureTypes = new ArrayList<FeatureType>();
        featureTypes.add(FeatureType.FORMS);
        featureTypes.add(FeatureType.TABLES);

        AnalyzeDocumentRequest analyzeDocumentRequest = AnalyzeDocumentRequest.builder()
                .featureTypes(featureTypes)
                .document(myDoc)
                .build();

        DetectDocumentTextRequest request = new DetectDocumentTextRequest().withDocument(

        AnalyzeDocumentResponse analyzeDocument = textractClient.analyzeDocument(analyzeDocumentRequest);
        List<Block> docInfo = analyzeDocument.blocks();
        Iterator<Block> blockIterator = docInfo.iterator();

        while(blockIterator.hasNext()) {
            Block block = blockIterator.next();
            System.out.println("The block type is " +block);
        }*/
    }
}

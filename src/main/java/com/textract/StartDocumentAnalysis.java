package com.textract;// snippet-sourcedescription:[com.textract.StartDocumentAnalysis.java demonstrates how to start the asynchronous analysis of a document.]
// snippet-keyword:[AWS SDK for Java v2]
// snippet-service:[Amazon Textract]
// snippet-keyword:[Code Sample]
// snippet-sourcetype:[full-example]
// snippet-sourcedate:[09/29/2021]
// snippet-sourceauthor:[scmacdon - AWS]

/*
   Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
   SPDX-License-Identifier: Apache-2.0
*/


// snippet-start:[textract.java2._start_doc_analysis.import]
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.textract.model.S3Object;
import software.amazon.awssdk.services.textract.TextractClient;
import software.amazon.awssdk.services.textract.model.StartDocumentAnalysisRequest;
import software.amazon.awssdk.services.textract.model.DocumentLocation;
import software.amazon.awssdk.services.textract.model.TextractException;
import software.amazon.awssdk.services.textract.model.StartDocumentAnalysisResponse;
import software.amazon.awssdk.services.textract.model.GetDocumentAnalysisRequest;
import software.amazon.awssdk.services.textract.model.GetDocumentAnalysisResponse;
import software.amazon.awssdk.services.textract.model.FeatureType;
import java.util.ArrayList;
import java.util.List;

import static java.sql.DriverManager.println;
// snippet-end:[textract.java2._start_doc_analysis.import]

public class StartDocumentAnalysis {

    private static final HotFolderCaller hotFolderCaller = new HotFolderCaller();

    public static void main(String[] args) {
        hotFolderCaller.watchFolder();
    }
}
package com.textract;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static java.nio.file.LinkOption.NOFOLLOW_LINKS;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.List;

import org.apache.commons.io.FileUtils;


public class HotFolderCaller {

    private String path = "D:\\Temp\\InputTrigger";
    private static final DocumentProcess DOCUMENT_PROCESS = new DocumentProcess();

    public HotFolderCaller(){}

    public HotFolderCaller(String path){
        this.path = path;
    }

    public void watchFolder()
    {
        String name = "";
        File dir = new File(path);
        Path myDir= dir.toPath();
        try
        {
            Boolean isFolder = (Boolean) Files.getAttribute(myDir,"basic:isDirectory", NOFOLLOW_LINKS);
            if (!isFolder)
            {
                throw new IllegalArgumentException("Path: " + myDir + " is not a folder");
            }
        }
        catch (IOException ioe)
        {
            ioe.printStackTrace();
        }

        System.out.println("Watching path: " + myDir);

        try {
            WatchService watcher = myDir.getFileSystem().newWatchService();
            myDir.register(watcher, StandardWatchEventKinds.ENTRY_CREATE);

            WatchKey watckKey = watcher.take();

            List<WatchEvent<?>> events = watckKey.pollEvents();

            for (WatchEvent event : events) {
                if (event.kind() == StandardWatchEventKinds.ENTRY_CREATE) {
                    System.out.println("Created: " + event.kind().toString());
                    File file = new File(path);
                    for(File f : file.listFiles()) {
                        //System.out.println(f.getName());
                        name = f.getName();
                    }

                    FileUtils.cleanDirectory(new File(path));

                    //TEXTRACT
                    //DOCUMENT_PROCESS.processDocuments(name);

                    //GOOGLE
                    //DOCUMENT_PROCESS.processDocumentGoogle(name);

                    //AZURE
                    DOCUMENT_PROCESS.processDocumentsAzure(name);

                    DOCUMENT_PROCESS.deleteFiles();
                }
            }

        }
        catch (Exception e)
        {
            System.out.println("Error: " + e.toString());
        }
        watchFolder();
    }
}

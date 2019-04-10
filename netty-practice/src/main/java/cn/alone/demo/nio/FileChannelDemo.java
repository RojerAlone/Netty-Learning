package cn.alone.demo.nio;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

/**
 * Created by RojerAlone on 2019-04-10
 */
public class FileChannelDemo {

    private static final String fromFilePath = "nio_from_file.txt";
    private static final String toFilePath = "nio_to_file.txt";
    private static final int transformBucketSize = 2;

    @SuppressWarnings("ResultOfMethodCallIgnored")
    public static void main(String[] args) throws IOException {
        File fromFile = new File(fromFilePath);
        fromFile.delete();
        fromFile.createNewFile();
        RandomAccessFile baseFile = new RandomAccessFile(fromFilePath, "rw");
        FileChannel baseFileChannel = baseFile.getChannel();
        baseFileChannel.write(ByteBuffer.wrap("hello world".getBytes()));
        RandomAccessFile writeFile = new RandomAccessFile(toFilePath, "rw");
        FileChannel writeFileChannel = writeFile.getChannel();
        int position = 0;
        System.out.printf("base file channel size: %d \n", baseFileChannel.size());
        System.out.println("start write...");
        while (position < baseFileChannel.size()) {
            System.out.printf("write %d bytes from position %d \n",
                    Math.min(transformBucketSize, baseFileChannel.size() - position), position);
            baseFileChannel.transferTo(position, transformBucketSize, writeFileChannel);
            position += transformBucketSize;
        }
        System.out.println("write successfully");
        writeFileChannel.close();
        writeFile.close();
        baseFileChannel.close();
        baseFile.close();
        new File(fromFilePath).delete();
        new File(toFilePath).delete();
    }

}

package io.openio.hadoop;

import org.apache.hadoop.conf.Configured;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapred.JobConf;
import org.apache.hadoop.mapred.Mapper;
import org.apache.hadoop.mapred.OutputCollector;
import org.apache.hadoop.mapred.Reporter;

import java.io.Closeable;
import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;

public abstract class IOMapperBase<T> extends Configured
        implements Mapper<Text, LongWritable, Text, Text> {

    protected byte[] buffer;
    protected int bufferSize;
    protected FileSystem fs;
    protected String hostName;
    protected Closeable stream;

    public IOMapperBase() {
    }

    public void configure(JobConf conf) {
        setConf(conf);
        try {
            URI uri = new URI(conf.get("dfsio.base.dir"));
            fs = FileSystem.get(uri, conf);
        } catch (Exception e) {
            throw new RuntimeException("Cannot create file system.", e);
        }
        bufferSize = conf.getInt("dfsio.file.buffer.size", 4096);
        buffer = new byte[bufferSize];
        try {
            hostName = InetAddress.getLocalHost().getHostName();
        } catch(Exception e) {
            hostName = "localhost";
        }
    }

    public void close() throws IOException {
    }

    /**
     * Perform io operation, usually read or write.
     *
     * @param reporter
     * @param name file name
     * @param value offset within the file
     * @return object that is passed as a parameter to
     *          {@link #collectStats(OutputCollector,String,long,Object)}
     * @throws IOException
     */
    abstract T doIO(Reporter reporter,
                    String name,
                    long value) throws IOException;

    /**
     * Create an input or output stream based on the specified file.
     * Subclasses should override this method to provide an actual stream.
     *
     * @param name file name
     * @return the stream
     * @throws IOException
     */
    public Closeable getIOStream(String name) throws IOException {
        return null;
    }

    /**
     * Collect stat data to be combined by a subsequent reducer.
     *
     * @param output
     * @param name file name
     * @param execTime IO execution time
     * @param doIOReturnValue value returned by {@link #doIO(Reporter,String,long)}
     * @throws IOException
     */
    abstract void collectStats(OutputCollector<Text, Text> output,
                               String name,
                               long execTime,
                               T doIOReturnValue) throws IOException;
    /**
     * Map file name and offset into statistical data.
     * <p>
     * The map task is to get the
     * <tt>key</tt>, which contains the file name, and the
     * <tt>value</tt>, which is the offset within the file.
     *
     * The parameters are passed to the abstract method
     * {@link #doIO(Reporter,String,long)}, which performs the io operation,
     * usually read or write data, and then
     * {@link #collectStats(OutputCollector,String,long,Object)}
     * is called to prepare stat data for a subsequent reducer.
     */
    public void map(Text key,
                    LongWritable value,
                    OutputCollector<Text, Text> output,
                    Reporter reporter) throws IOException {
        String name = key.toString();
        long longValue = value.get();

        reporter.setStatus("starting " + name + " ::host = " + hostName);

        this.stream = getIOStream(name);
        T statValue = null;
        long tStart = System.currentTimeMillis();
        try {
            statValue = doIO(reporter, name, longValue);
        } finally {
            if(stream != null) stream.close();
        }
        long tEnd = System.currentTimeMillis();
        long execTime = tEnd - tStart;
        collectStats(output, name, execTime, statValue);

        reporter.setStatus("finished " + name + " ::host = " + hostName);
    }
}

package com.documents4j.conversion;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.ToIntFunction;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zeroturnaround.exec.ProcessExecutor;
import org.zeroturnaround.exec.ProcessResult;
import org.zeroturnaround.exec.stream.slf4j.Slf4jStream;

import com.documents4j.throwables.ConverterAccessException;
import com.documents4j.util.OsUtils;

public abstract class AbstractExternalConverter implements IExternalConverter {

    private final Logger logger;

    private final long processTimeout;
    private final File baseFolder;

    public AbstractExternalConverter(File baseFolder, long processTimeout, TimeUnit processTimeoutUnit) {
        this.logger = LoggerFactory.getLogger(getClass());
        this.baseFolder = baseFolder;
        this.processTimeout = processTimeoutUnit.toMillis(processTimeout);
    }

    protected static String quote(String... args) {
        StringBuilder stringBuilder = new StringBuilder();
        boolean first = true;
        for (String arg : args) {
            if (first) {
                first = false;
            } else {
                stringBuilder.append(' ');
            }
            stringBuilder.append('"').append(arg.replace("\"", "\"\"")).append('"');
        }
        return stringBuilder.toString();
    }

    protected static String doubleQuote(String... args) {
        return "\"" + quote(args) + "\"";
    }

    protected File getBaseFolder() {
        return baseFolder;
    }

    protected long getProcessTimeout() {
        return processTimeout;
    }


    protected ProcessExecutor makePresetProcessExecutor() {
        return makePresetProcessExecutor(Slf4jStream.of(logger).asInfo());
    }

    protected ProcessExecutor makePresetProcessExecutor(OutputStream outputStream) {
        return new ProcessExecutor()
                .redirectOutput(outputStream)
                .redirectError(Slf4jStream.of(logger).asInfo())
                .readOutput(true)
                .directory(getBaseFolder())
                .timeout(getProcessTimeout(), TimeUnit.MILLISECONDS)
                .exitValueAny();
    }

    protected int runNoArgumentScript(File script) {
        logger.trace("Execute no-argument script {}", script);
        try {
            // Do not kill this process on a JVM shut down! A script for e.g. shutting down MS Word
            // would typically be triggered from a shut down hook. Therefore, the shut down process
            // should never be killed during JVM shut down. In order to avoid an incomplete start up
            // procedure, start up processes will never be killed either.
            String[] command = null;

            int exitCode = -100;
            if (OsUtils.isWindows()) {
                command = new String[]{"cmd", "/S", "/C", doubleQuote(script.getAbsolutePath())};
                exitCode = makePresetProcessExecutor()
                               .command(command)
                               .execute().getExitValue();
            } else if (OsUtils.isMac()) {
                command = new String[]{"/usr/bin/osascript", script.getAbsolutePath()};
                ProcessResult res = makePresetProcessExecutor().command(command).execute();
                ToIntFunction<ProcessResult> extractor = ( processResult -> Integer.parseInt(new String(processResult.output(), StandardCharsets.UTF_8).replaceAll("\\n", "")) );
                exitCode = extractor.applyAsInt(res);
            }
            logger.trace("Got exitcode {} for command {}", exitCode, Arrays.toString(command));
            return exitCode;
        } catch (IOException e) {
            String message = String.format("Unable to run script: %s", script);
            logger.error(message, e);
            throw new ConverterAccessException(message, e);
        } catch (InterruptedException e) {
            String message = String.format("Thread responsible for running script was interrupted: %s", script);
            logger.error(message, e);
            throw new ConverterAccessException(message, e);
        } catch (TimeoutException e) {
            String message = String.format("Thread responsible for running script timed out: %s", script);
            logger.error(message, e);
            throw new ConverterAccessException(message, e);
        }
    }

    protected void tryDelete(File file) {
        if (!file.delete()) {
            logger.warn("Cannot delete file: {}", file);
        }
    }
}

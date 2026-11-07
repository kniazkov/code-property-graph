/* Deterministic deadline and forced-cleanup checks: no sleeping child. MIT License. */
package com.kniazkov.cpg.e2e;
import java.io.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
class CliSandboxTest {
    @TempDir Path temp;
    private Path errors() throws IOException { Path p=temp.resolve("stderr.txt");Files.writeString(p,"child diagnostic");return p; }
    @Test void defaultBudgetRemainsFortyFiveSeconds() throws Exception {
        var child=new FakeProcess(true);
        assertEquals(0,new CliSandbox(temp).awaitExit(child,List.of("java"),errors()));
        assertEquals(Duration.ofSeconds(45).toNanos(),child.timeout);assertEquals(TimeUnit.NANOSECONDS,child.unit);
        assertFalse(child.destroyed);assertFalse(child.reaped);
    }
    @Test void explicitBudgetReachesTheSameWaitPath() throws Exception {
        var child=new FakeProcess(true);
        assertEquals(0,new CliSandbox(temp,Duration.ofSeconds(90)).awaitExit(child,List.of("java"),errors()));
        assertEquals(Duration.ofSeconds(90).toNanos(),child.timeout);assertFalse(child.destroyed);
    }
    @Test void invalidBudgetsAreRejectedBeforeCreatingInputs() {
        for(Duration invalid:List.of(Duration.ZERO,Duration.ofSeconds(-1),Duration.ofSeconds(Long.MAX_VALUE)))
            assertThrows(IllegalArgumentException.class,()->new CliSandbox(temp,invalid));
        assertThrows(NullPointerException.class,()->new CliSandbox(temp,null));
        assertFalse(Files.exists(temp.resolve("input sources")));
    }
    @Test void timeoutStillFailsAndForciblyReapsTheChild() throws Exception {
        var child=new FakeProcess(false);var cli=new CliSandbox(temp,Duration.ofSeconds(90));Path err=errors();
        var failure=assertThrows(AssertionError.class,()->cli.awaitExit(child,List.of("java","--stage","green"),err));
        assertTrue(failure.getMessage().contains("PT1M30S"));assertTrue(failure.getMessage().contains("child diagnostic"));
        assertTrue(child.destroyed);assertTrue(child.reaped);
    }
    @Test void interruptionStillForciblyReapsTheChild() throws Exception {
        var child=new FakeProcess(false);child.interrupted=true;var cli=new CliSandbox(temp);Path err=errors();
        assertThrows(InterruptedException.class,()->cli.awaitExit(child,List.of("java"),err));
        assertTrue(child.destroyed);assertTrue(child.reaped);
    }
    private static final class FakeProcess extends Process {
        final boolean finishes;boolean alive=true,destroyed,reaped,interrupted;long timeout;TimeUnit unit;
        FakeProcess(boolean finishes){this.finishes=finishes;}
        @Override public boolean waitFor(long timeout,TimeUnit unit)throws InterruptedException{
            this.timeout=timeout;this.unit=unit;if(interrupted)throw new InterruptedException("test interruption");
            if(finishes)alive=false;return finishes;
        }
        @Override public int waitFor(){reaped=true;alive=false;return 0;}
        @Override public int exitValue(){if(alive)throw new IllegalThreadStateException();return 0;}
        @Override public boolean isAlive(){return alive;}
        @Override public void destroy(){destroyed=true;alive=false;}
        @Override public Process destroyForcibly(){destroy();return this;}
        @Override public OutputStream getOutputStream(){return OutputStream.nullOutputStream();}
        @Override public InputStream getInputStream(){return InputStream.nullInputStream();}
        @Override public InputStream getErrorStream(){return InputStream.nullInputStream();}
    }
}

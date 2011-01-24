package org.broadinstitute.sting.utils.threading;

import org.apache.log4j.Logger;
import org.broadinstitute.sting.utils.GenomeLoc;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Thread-safe shared memory only implementation
 */
public class SharedMemoryGenomeLocProcessingTracker extends GenomeLocProcessingTracker {
    private List<ProcessingLoc> newPLocs = new ArrayList<ProcessingLoc>();

    protected SharedMemoryGenomeLocProcessingTracker(ClosableReentrantLock lock) {
        super(lock, null);
    }

    protected SharedMemoryGenomeLocProcessingTracker(ClosableReentrantLock lock, PrintStream status) {
        super(lock, status);
    }

    @Override
    protected void registerNewLocs(Collection<ProcessingLoc> plocs) {
        newPLocs.addAll(plocs);
    }

    @Override
    protected List<ProcessingLoc> readNewLocs() {
        List<ProcessingLoc> r = newPLocs;
        newPLocs = new ArrayList<ProcessingLoc>();
        return r;
    }
}

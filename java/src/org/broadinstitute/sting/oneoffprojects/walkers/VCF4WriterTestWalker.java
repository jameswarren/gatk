package org.broadinstitute.sting.oneoffprojects.walkers;

import org.broad.tribble.util.AsciiLineReader;
import org.broad.tribble.vcf.*;
import org.broadinstitute.sting.commandline.Argument;
import org.broadinstitute.sting.gatk.contexts.AlignmentContext;
import org.broadinstitute.sting.gatk.contexts.ReferenceContext;
import org.broadinstitute.sting.gatk.contexts.variantcontext.VariantContext;
import org.broadinstitute.sting.gatk.datasources.simpleDataSources.ReferenceOrderedDataSource;
import org.broadinstitute.sting.gatk.refdata.RefMetaDataTracker;
import org.broadinstitute.sting.gatk.refdata.features.vcf4.VCF4Codec;
import org.broadinstitute.sting.gatk.refdata.tracks.RMDTrack;
import org.broadinstitute.sting.gatk.walkers.RodWalker;
import org.broadinstitute.sting.utils.GenomeLoc;
import org.broadinstitute.sting.utils.StingException;
import org.broadinstitute.sting.utils.genotype.vcf.VCFReader;
import org.broadinstitute.sting.utils.genotype.vcf.VCFUtils;
import org.broadinstitute.sting.utils.genotype.vcf.VCFWriter;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.util.*;
/*
 * Copyright (c) 2009 The Broad Institute
 *
 * Permission is hereby granted, free of charge, to any person
 * obtaining a copy of this software and associated documentation
 * files (the "Software"), to deal in the Software without
 * restriction, including without limitation the rights to use,
 * copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the
 * Software is furnished to do so, subject to the following
 * conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES
 * OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT
 * HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR
 * OTHER DEALINGS IN THE SOFTWARE.
 */


/**
 * Prints out all of the RODs in the input data set. Data is rendered using the toString() method
 * of the given ROD.
 */
public class VCF4WriterTestWalker extends RodWalker<Integer, Integer> {
    private VCFWriter vcfWriter;

    @Argument(fullName="output_file", shortName="output", doc="VCF file to which output should be written", required=true)
    private String OUTPUT_FILE = null;


    public static final String INPUT_ROD_NAME = "vcf";

    protected static String line = null;
    final TreeSet<String> samples = new TreeSet<String>();
    VCF4Codec vcf4codec = new VCF4Codec();


    /**
     * Initialize the number of loci processed to zero.
     *
     * @return 0
     */
    public Integer reduceInit() { return 0; }

    public void initialize() {
        final List<ReferenceOrderedDataSource> dataSources = this.getToolkit().getRodDataSources();


        // Open output file specified by output VCF ROD
        Set<VCFHeaderLine> hInfo = new HashSet<VCFHeaderLine>();
        hInfo.addAll(VCFUtils.getHeaderFields(getToolkit()));


        vcfWriter = new VCFWriter(new File(OUTPUT_FILE));
        VCFHeader header = null;
        for( final ReferenceOrderedDataSource source : dataSources ) {
            final RMDTrack rod = source.getReferenceOrderedData();
            if(rod.getName().equalsIgnoreCase(INPUT_ROD_NAME)) {
                if (rod.getType().equals(vcf4codec.getClass())) {

                    try {
                        AsciiLineReader lineReader = new AsciiLineReader(new FileInputStream(rod.getFile().getAbsolutePath()));
                        int lineNumber = vcf4codec.readHeader(lineReader);
                        out.printf("Read %d header lines%n", lineNumber);

                        header = vcf4codec.getHeader(VCFHeader.class);
                    }
                    catch (FileNotFoundException e ) {
                        throw new StingException(e.getMessage());
                    }
                } else {
                    final VCFReader reader = new VCFReader(rod.getFile());
                    final Set<String> vcfSamples = reader.getHeader().getGenotypeSamples();
                    samples.addAll(vcfSamples);
                    reader.close();
                    header = new VCFHeader(hInfo, samples);
                }

            }
        }

        if ( header != null )
            header.setVersion(VCFHeaderVersion.VCF4_0);

        vcfWriter.writeHeader(header);

    }

    /**
     *
     * @param tracker  the meta-data tracker
     * @param ref      the reference base
     * @param context  the context for the given locus
     * @return 1 if the locus was successfully processed, 0 if otherwise
     */
    public Integer map(RefMetaDataTracker tracker, ReferenceContext ref, AlignmentContext context) {
        if ( tracker == null )
            return 0;

        GenomeLoc loc = context.getLocation();
        VariantContext vc = tracker.getVariantContext(ref,"vcf", null, loc, true);


        if (vc == null)
            return 0;

        // Write directly variant context to VCF4.0 format.
        vcfWriter.add(vc, ref.getBases());

        return 1;
    }

    /**
     * Increment the number of rods processed.
     *
     * @param value result of the map.
     * @param sum   accumulator for the reduce.
     * @return the new number of rods processed.
     */
    public Integer reduce(Integer value, Integer sum) {
        return sum + value;
    }

    public void onTraversalDone(Integer result) {}
}
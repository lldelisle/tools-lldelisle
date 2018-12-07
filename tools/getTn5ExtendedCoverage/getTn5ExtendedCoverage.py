import argparse

import pysam


# Inspired by bedtools
def reportCoverage(chrName, starts, ends, fileout, lengthChrom):
    """ Write in the fileout the coverage in bedgraph format"""
    currentDepth = 0
    lastStart = -1
    lastDepth = -1
    minindex = max(1, min(starts.keys()))
    maxindex = min(lengthChrom-1, max(ends.keys()))
    for i in range(minindex, maxindex):
        if (i in starts):
            currentDepth += starts[i]
        if (currentDepth != lastDepth):
            # The coverage changed
            if (lastDepth > 0):
                # Only non 0 regions are reported
                fileout.write(chrName + "\t" + str(lastStart) + "\t" +
                              str(i) + "\t" + str(lastDepth) + "\n")
            # We update the parameters
            lastStart = i
            lastDepth = currentDepth
        if (i in ends):
            # We remove the ends to update in the next round
            currentDepth -= ends[i]
    # Print the last
    if (lastDepth > 0):
        fileout.write(chrName + "\t" + str(lastStart) + "\t" +
                      str(maxindex+1) + "\t" + str(lastDepth) + "\n")


def fiveP_shifted_oneB_read_start(read):
    if read.is_reverse:
        # Read is aligned reverse we remove 5 bases because the Tn5 duplicates 9 pb
        return read.reference_end-5
    else:
        # Read is aligned forward we had 4 bases because the Tn5 duplicates 9 bp
        # We need to add 1 because a bam file is 0-based.
        return read.reference_start+1+4
  

def readBamAndComputeShiftedCoverage(inbam, outBed, l):
    with open(outBed, 'w') as fo:
        with pysam.AlignmentFile(inbam, 'rb') as bamfile:
            gen = dict(zip(bamfile.references, bamfile.lengths))
            for curChr in gen:
                starts = {}
                ends = {}
                for read in bamfile.fetch(curChr):
                    centralPos = fiveP_shifted_oneB_read_start(read)
                    start = max(1, centralPos-lengthToExtend)
                    end = min(centralPos+lengthToExtend-1, gen[curChr]-1)
                    starts[start] = starts.setdefault(start, 0)+1
                    ends[end] = ends.setdefault(end, 0)+1
                if starts != {}:
                    reportCoverage(curChr, starts, ends, fo, gen[curChr])


argp = argparse.ArgumentParser(
    description=("compute coverage like bedtools"
               " of 5' of reads shifted 4 or 5 bases and extended of length."))
argp.add_argument('--input', default=None,
                  help="input coordinates-sorted bam with alignement.")
argp.add_argument('--length', default=None)
argp.add_argument('--output', default=None)
args = argp.parse_args()
lengthToExtend = int(args.length)
readBamAndComputeShiftedCoverage(args.input, args.output, lengthToExtend)

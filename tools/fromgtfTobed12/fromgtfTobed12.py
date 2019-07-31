import argparse
import sys

import gffutils


def convert_gtf_to_bed(fn, fo, useGene):
    db = gffutils.create_db(fn, ':memory:')
    # For each transcript:
    prefered_name = "transcript_name"
    if useGene:
        prefered_name = "gene_name"
    for tr in db.features_of_type("transcript", order_by='start'):
        # The name would be the name of the transcript/gene if exists
        try:
            trName = tr.attributes[prefered_name][0]
        except KeyError:
            # Else try to guess the name of the transcript/gene from exons:
            try:
                trName = set([e.attributes[prefered_name][0]
                              for e in
                              db.children(tr,
                                          featuretype='exon',
                                          order_by='start')]).pop()
            except KeyError:
                # Else take the transcript id
                trName = tr.id
        # If the cds is defined in the gtf,
        # use it to define the thick start and end
        # The gtf is 1-based closed intervalls and
        # bed are 0-based half-open so:
        # I need to remove one from each start
        try:
            cds_start = next(db.children(tr,
                                         featuretype='CDS',
                                         order_by='start')).start - 1
            cds_end = next(db.children(tr,
                                       featuretype='CDS',
                                       order_by='-start')).end
        except StopIteration:
            cds_start = tr.start - 1
            cds_end = tr.start - 1
        # Get all exons starts and end to get lengths
        exons_starts = [e.start - 1
                        for e in
                        db.children(tr, featuretype='exon', order_by='start')]
        exons_ends = [e.end
                      for e in
                      db.children(tr, featuretype='exon', order_by='start')]
        exons_length = [e - s for s, e in zip(exons_starts, exons_ends)]
        fo.write("chr%s\t%d\t%d\t%s\t%d\t%s\t%d\t%d\t%s\t%d\t%s\t%s\n" %
                 (tr.chrom, tr.start - 1, tr.end, trName, 0, tr.strand,
                  cds_start, cds_end, "0", len(exons_starts),
                  ",".join([str(l) for l in exons_length]),
                  ",".join([str(s - (tr.start - 1)) for s in exons_starts])))


argp = argparse.ArgumentParser(
  description=("Convert a gtf to a bed12 with one entry"
               " per transcript"))
argp.add_argument('input', default=None,
                  help="Input gtf file (can be gzip).")
argp.add_argument('--output', default=sys.stdout,
                  type=argparse.FileType('w'),
                  help="Output bed12 file.")
argp.add_argument('--useGene', action="store_true",
                  help="Use the gene name instead of the "
                       "transcript name.")
args = argp.parse_args()
convert_gtf_to_bed(args.input, args.output, args.useGene)

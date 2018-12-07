import argparse

import cooler


def checkInput(args):
  if args.r2 is None:
    args.r2 = args.r1


def getHeaders(c, r, a):
  fragID = c.bins().fetch(r).axes[0]
  chromID = c.bins().fetch(r).as_matrix(['chrom'])
  starts = c.bins().fetch(r).as_matrix(['start'])
  ends = c.bins().fetch(r).as_matrix(['end'])
  return(["%s|%s|%s:%i-%i" % (i, a, chromid[0], s, e) 
          for i, chromid, s, e in zip(fragID, chromID, starts, ends)])


def extractMatrix(args):
  coolerInput = cooler.Cooler(args.input)
  mat = coolerInput.matrix(balance=args.balance).fetch(args.r1, args.r2)
  if args.header:
    ass = coolerInput.info['genome-assembly']
    rowH = getHeaders(coolerInput, args.r1, ass)
    colH = getHeaders(coolerInput, args.r2, ass)
    with open(args.output, 'w') as f:
      line = "%ix%i" % (len(rowH), len(colH)) + '\t' + '\t'.join(colH)
      f.write(line + '\n')
      for i, row in enumerate(mat):
        line = rowH[i] + '\t' + '\t'.join("%.6g" % value for value in row)
        f.write(line + '\n')
  else:
    with open(args.output, 'w') as f:
      for row in mat:
        line = '\t'.join("%.6g" % value for value in row)
        f.write(line + '\n')


argp = argparse.ArgumentParser(description='Extract a matrix from a cool file.')
argp.add_argument('--input', default=None, help='a cool file to extract from.')
argp.add_argument('--output', default=None,
                  help='a txt file with matrix values.')
argp.add_argument('--header', action="store_true",
                  help=("If you want to print row names and col names"
                        " (by default there are only values)."))
argp.add_argument('--balance', action="store_true",
                  help=("If you want the balanced values"
                        " (by default it is the raw)."))
argp.add_argument('--r1', default=None,
                  help='the region 1 on which you should generate the matrix')
argp.add_argument('--r2', default=None,
                  help='the region 2 if different from region1.')

args = argp.parse_args()
checkInput(args)
extractMatrix(args)

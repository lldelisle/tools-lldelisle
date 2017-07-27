import cooler
import sys
import os
import argparse

def checkInput(args):
  if args.r2 is None:
    args.r2=args.r1

def extractMatrix(args):
  c = cooler.Cooler(args.input)
  mat=c.matrix(balance=args.balance).fetch(args.r1,args.r2)
  with open(args.output, 'w') as f:
    for row in mat:
      line = '\t'.join("%.6g"% value for value in row)
      f.write(line + '\n')

argp=argparse.ArgumentParser(description='Extract a matrix from a cool file.')
argp.add_argument('--input',default=None, help='a cool file to extract from.')
argp.add_argument('--output',default=None, help='a txt file with matrix values.')
argp.add_argument('--balance',action="store_true", help='If you want the balanced values (by default it is the raw).')
argp.add_argument('--r1',default=None,help='the region 1 on which you should generate the matrix')
argp.add_argument('--r2',default=None,help='the region 2 if different from region1.')

args = argp.parse_args()
checkInput(args)
extractMatrix(args)

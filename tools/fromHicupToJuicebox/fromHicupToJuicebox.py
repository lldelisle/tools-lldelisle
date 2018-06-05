from __future__ import print_function
import sys
import os
import argparse
import pysam
import subprocess
import bisect
from signal import signal, SIGPIPE, SIG_DFL
signal(SIGPIPE,SIG_DFL)

class Error(Exception):
    """Base class for exceptions in this module."""
    pass

class InputError(Error):
    """Exception raised for errors in the input.

    Attributes:
        expr -- input expression in which the error occurred
        msg  -- explanation of the error
    """
    def __init__(self, expr, msg):
        self.expr = expr
        self.msg = msg
    def __str__(self):
        return self.expr+"\n"+self.msg

def find_FragAndMid(chrBiDic, x):
    'Find fragID and midPosition value for the x coordinates'
    i = bisect.bisect_left(chrBiDic['starts'], x)
    if i:
        currentStart=chrBiDic['starts'][i-1]
        return(chrBiDic['dic'][str(currentStart)])
    raise ValueError


def loadFragFile(fragmentFile,colC,colS,colE,colI,headerSize):
  dicFragIDStart={}
  currentChr=""
  currentListOfStarts=[]
  currentDic={}
  with open(fragmentFile,'r') as f:
    for i,line in enumerate(f):
      if(i<headerSize):
        continue
      v=line.split()
      if(len(v)<max(colC,colS,colE,colI)):
        raise InputError(len(v)<max(colC,colS,colE,colI),"The ids of columns specified in the input for the fragment file are incompatible with the line :"+line)
      if(v[colC]==currentChr):
        currentListOfStarts.append(int(v[colS]))
      else:
        dicFragIDStart[currentChr]={'starts':currentListOfStarts,'dic':currentDic}
        currentChr=v[colC]
        currentListOfStarts=[int(v[colS])]
        currentDic={}
      currentDic[v[colS]]=[int(v[colI]),int((int(v[colE])+int(v[colS]))/2)]
  dicFragIDStart[currentChr]={'starts':currentListOfStarts,'dic':currentDic}
  return(dicFragIDStart)

def fiveP_oneB_read_start(read):
    if read.is_reverse:
        return read.reference_end
    else:
        return read.reference_start+1

def readSamFromHicupAndWriteOutputForJuicebox(in_samOrBam,fo,useMid,bigDic,method):
  """"return the validpair file <readname> <str1> <chr1> <pos1> <frag1> <str2> <chr2> <pos2> <frag2> <mapq1> <mapq2> str = strand (0 for forward, anything else for reverse) pos = 5'of the read (position used to find the fragment in the 2 restriction enzyme mode but not for the sonication protocol where the position used to find the fragment is 10 bp downstream the most upstream coordinate of the mapped positions (the one given by bowtie))."""
  currentId=""
  singleReads=0
  informativeLineNumber=0
  pairOnGoing=False
  os.mkfifo('bampipe')
  command = 'samtools view -h '+in_samOrBam+' > bampipe'
  p = subprocess.Popen(command, shell=True)
  with pysam.Samfile('bampipe', 'r') as f:
    for read in f.fetch():
      informativeLineNumber+=1
      bowtieReadPos=read.reference_start+1
      refName=read.reference_name
      if method=='hicup':
        fragInfo=find_FragAndMid(bigDic[refName],bowtieReadPos+10)#To follow hicup_fiter<=6.1.0
      elif method=='hiclib':
        if read.is_reverse:
          fragInfo=find_FragAndMid(bigDic[refName],read.reference_end-4)
        else:
          fragInfo=find_FragAndMid(bigDic[refName],bowtieReadPos+4)
      if useMid:
        currentPos=fragInfo[1]
      else:
        currentPos=fiveP_oneB_read_start(read)
      if not pairOnGoing:
        readname=read.qname.split("/")[0]
        str1=int(read.is_reverse)
        chr1=refName
        pos1=currentPos
        frag1=fragInfo[0]
        mapq1=read.mapping_quality
        pairOnGoing=True
      else:
        #This should be the same id
        if readname!=read.qname.split("/")[0]:
          singleReads+=1
          print(readname+" is a single read or the sam/bam is not sorted by read id.")
          readname=read.qname.split("/")[0]
          str1=int(read.is_reverse)
          chr1=refName
          pos1=currentPos
          frag1=fragInfo[0]
          mapq1=read.mapping_quality
          if(singleReads>10 and informativeLineNumber<20):
            raise Exception("The sam/bam is probably not sorted by qname. Job stopped.")
        else:
          str2=int(read.is_reverse)
          chr2=refName
          pos2=currentPos
          frag2=fragInfo[0]
          mapq2=read.mapping_quality
          pairOnGoing=False
          fo.write("%s\t%i\t%s\t%i\t%i\t%i\t%s\t%i\t%i\t%i\t%i\n"%(readname, str1, chr1, pos1, frag1, str2, chr2, pos2, frag2, mapq1, mapq2))

argp=argparse.ArgumentParser(description="Convert the output of hicup (as sam or bam) to the input of juicebox (<readname> <str1> <chr1> <pos1> <frag1> <str2> <chr2> <pos2> <frag2> <mapq1> <mapq2> str = strand (0 for forward, anything else for reverse) pos = 5'of the read unless --useMid is used.)")
argp.add_argument('sam',default=None,help="Input sam or bam with pairs like hicup output.")
argp.add_argument('--output',default=sys.stdout,type=argparse.FileType('w'), help="Output valid pair file.")
argp.add_argument('--fragmentFile',default=None, help="A file containing the coordinates of each fragment id.")
argp.add_argument('--colForChr',default=1, help="The number of the column for the chromosome in the fragment file.",type=int)
argp.add_argument('--colForStart',default=2, help="The number of the column for the start position in the fragment file.",type=int)
argp.add_argument('--colForEnd',default=3, help="The number of the column for the end position in the fragment file.",type=int)
argp.add_argument('--colForID',default=4, help="The number of the column for the fragment id in the fragment file.",type=int)
argp.add_argument('--lineToSkipInFragmentFile',default=0,help="The number line to skip in the fragment file.",type=int)
argp.add_argument('--useMid', help="Use the middle of the fragments instead of the 5' position.", action="store_true")
argp.add_argument('--methodForFrag',help="Which method use to determine to which fragment belong a read. hicup is 10 bp downstream the most upstream coordinate. hiclib is 4 bases after the 5' if strand is + and 4 bases before if strand is -.", choices=['hicup','hiclib'], default='hicup')
args = argp.parse_args()
print("Processing fragment file...",file=sys.stderr)
bigDic=loadFragFile(args.fragmentFile,args.colForChr-1,args.colForStart-1,args.colForEnd-1,args.colForID-1,args.lineToSkipInFragmentFile)
print("Fragment file processed.",file=sys.stderr)
readSamFromHicupAndWriteOutputForJuicebox(args.sam,args.output,args.useMid,bigDic,args.methodForFrag)

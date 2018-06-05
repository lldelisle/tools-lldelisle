#Switch mate1 and mate2 to have chromosome of mate1<=chromosome of mate2
#sort the chromosomes to be compatible with juicebox
awk '{chr1=$3;chr2=$7;if(chr1<chr2){print $0}else if(chr1==chr2){pos1=$4;pos2=$8;if(pos1<=pos2){print $0}else{print $1"\t"$6"\t"$7"\t"$8"\t"$9"\t"$2"\t"$3"\t"$4"\t"$5"\t"$11"\t"$10}}else{print $1"\t"$6"\t"$7"\t"$8"\t"$9"\t"$2"\t"$3"\t"$4"\t"$5"\t"$11"\t"$10}}' $1 | sort -k3,3 -k7,7 -k4,4n -k8,8n > $2

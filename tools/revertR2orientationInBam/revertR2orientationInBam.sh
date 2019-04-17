#!/bin/bash

inputBam=$1
outputBam=$2

samtools view -h "$inputBam" | awk -v OFS="\t" '{
    # Process only non header lines
    if(!($1~/^@/)){
        # SAM flag is field 2
        n=$2
        # Change only the second in pair flag is 128
        d=128
        q=(n-n%d)/d+(n<0)
        if(q%2==1){
            # Evaluate the strand reverse strand flag is 16
            d=16
            q=(n-n%d)/d+(n<0)
            if(q%2==1){
                # It is reverse it is now forward
                $2-=16
            }else{
                # It is forward it is now reverse
                $2+=16
            }
        }
    }
    print
}' | samtools view -b - > "$outputBam"
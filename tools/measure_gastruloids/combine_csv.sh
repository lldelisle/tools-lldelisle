#!/bin/bash
# I assume:
# input images are in input
# output csv are in output
# If there is no csv it will exit 1
# Else it will generate a summary results
# Which is a concatenation of all summary results (header only once)
# Will add a line for images without results
# attributes contains key1=value1,key2=value2 with default values

attributes=$1
output=$2
suffix=$3

# Check if there are some results:
nb_results_files=$(ls output | wc -l)
if [[ $nb_results_files -eq 0 ]]; then
    echo "No result found"
    exit 1
fi

# Start with the header
header=$(head -n 1 $(ls output/*csv | head -n 1))
echo $header > $output
for img in input/*; do
    result_file=output/$(basename $img)__Results.csv
    if [ ! -e $result_file ]; then
        echo "$result_file does not exists"
        result_file=output/$(basename $img $suffix)__Results.csv
        echo "trying $result_file"
    fi
    if [ -e $result_file ]; then
        # If there is a results file
        # Add the results from line 2 to the output
        echo "found"
        tail -n +2 $result_file >> $output
    else
        # If there is no result file
        # Fill the date and version and leave empty the other columns
        echo $header | awk -F ',' -v OFS="," -v label=$(basename $img $suffix) -v at="$attributes" -v date=$(date +%Y-%m-%d_%H-%M) '
BEGIN{
    split(at, list, ",")
    for (i in list) {
        split(list[i],kv,"=")
        defaultV[kv[1]] = kv[2]
    }
}
{
    for (i=1;i<=NF;i++){
        if ($i == "Label"){
            $i = label
        } else if ($i == "Date"){
            $i = date
        } else if ($i in defaultV){
            $i = defaultV[$i]
        } else {
            $i=""
        }
    }
    print
}' >> $output
    fi
done

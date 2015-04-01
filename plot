set terminal pngcairo  transparent enhanced font "arial,10" fontscale 1.0 size 660, 420
set output 'simple.1.png'
# put it to eps (vectors)
# change to black and white
set key inside left top vertical Right noreverse enhanced autotitles box linetype -1 linewidth 1.000

#set yrange [0:800001]

set title "CYCLON, 1.000 Nodes, c = 7, l = 3, churn from cycle 2202 on"

set pointintervalbox 1

plot [0:1000000] "d.txt" using ($0+1):1 title "scamplon 1.000" with lines, \
                 "a.txt" using ($0+1):1 title "scamplon 1.000" with lines

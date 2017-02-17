The burden of Big Data 
======================= 

Big data is the processing of large amounts of information. Big data, as defined by IBM, has three features, volume, variety, velocity. 
Most people understand big data, in terms of volume.


After some experience, one realises that velocity is a big problem when processing big data.  Big data inadvertently leads to long processing times this is inherent in large amount of data especially when required to find relationships between billions of small  chunks of data.  This means, one has to gather all this data into one place and process them on a specific algorithm. One of the techniques of doing this,  is Map Reduce. MapReduce, is an algorithm produced by Google. While Google’s own implementation of this algorithm has not been open sourced, there have been custom implementations of this algorithm. One such algorithm is Hadoop MapReduce.  This form of processing requires time. Volume and velocity don’t play well together. One needs to have a lot of discipline to solve this problem of volumes into velocity.


One of the biggest challenges that come with big data processing is variety. One of the  common terms we encounter  in big data-processing’s uniqueness. A very common trade-off  during  processing is to observe commonality among data and use that  to save  computation of power. When talking about uniqueness this trait cannot be exploited. When this trait cannot be exploited the computational power can’t be optimised. This in turn means a larger cost of processing data.  
If you were to put piece in a simple formula to make it into easier to understand big data it goes as this:


> Burden  =   Volume X Velocity X Variety 

A lot of big data experience boils down to calculating  burden.  while this equation does not depict a proper mathematical derivation, it will give you a clear idea about the challenges you would face when dealing with big data. While many foolishly jump around saying schema less, schema less, calculating this burden is closely linked  with big data schema design, especially   key design. 


 (Dictated  using Dragon Dictate, sorry for any typo)
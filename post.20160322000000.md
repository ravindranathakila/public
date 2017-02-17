Taming Your Classes 
===================

Is that class turning into Behemoth out of control? Maybe this would help in certain cases. This is Single Responsibility Principle as explained in Wikipedia:

"Martinn defines a responsibility as a  reason to change, and concludes that a class or module should have one, and only one, reason to change." 


"As an example, consider a module that compiles and prints a report. Imagine such a module can be changed for two reasons. First, the content of the report could change. Second, the format of the report could change. These two things change for very different causes; one substantive, and one cosmetic."


T"hee single responsibility principle says that these two aspects of the problem are really two separate responsibilities, and should therefore be in separate classes or modules. It would be a bad design to couple two things that change for different reasons at different times."
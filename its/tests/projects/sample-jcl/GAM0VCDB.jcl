//* Noncompliant@+2 {{Replace this implicit SYSIN DD * statement with an explicit one.}}
//
this is some value
//* ^[sc=1;ec=18]
//
//* Noncompliant@+2
//
implicit dd * with concatenated statement,
only this datastream should be highlighted
//* ^[sc=1;el=+1;ec=42]@-1
// DD DSN=CONCATENATED-STATEMENT
//*
//* Noncompliant@+2
//MYDD DD DNS=TEST
this is some value

//SYSIN DD *
some data
/*
//*
//SYSIN DD * DLM=AA
some data
AA
//*
//* Noncompliant@+2
//MYJOB JOB
some data

//* Noncompliant@+2
// CNTL
some data
// ENDCNTL
//*
//* Noncompliant@+2
// PROC
some data
// ENDPROC
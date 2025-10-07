We want to calculate embeddings of chunks of files in a code repository. We want to calculate embeddings via codevec to power a semantic search engine, that will:
- vectorize chunks of files in the current repo
- get a prompt from the user
- vectorize it
- use vector search (potentially enriched in a second step) to run the semantic search

So we need to do chuncking first.
We need to do that in Java.
Chunk size should be below a given threshold (e.g. 512 chars).
We need to chunk differently, according to file type.
For example:
- Java source by function or class, or block (to stay within chunk size limit)
- yaml file (find the way to chuck yaml file accordingly)
- etc. 
What are our possibilities?
For example:
- use an existing library in Java to do that
- use treesitter and traverse the AST, finding the right level of the tree that is less than the chunk size limit
- other solutions, please suggest

List pros and cons

----

There is another possibility from IntelliJ: use the PSI. Does it work for multiple languages? Can be extracted as a standalone library so that we don't depend on IntelliJ. We want this chunking to be working in any context: e.g. VSCode, not just IntelliJ.
Pros and Cons of this approach vs TreeSitter

----

Let's go with TreeSitter.
So, we want

# The repo we are working on
SonarLint Core.

# Place to start looking for integration in the code base
ClientFileSystemService: it's an RPC service that requests the list of files from the client by calling "listFiles".
After "var files = future.join().getFiles();" in getClientFileDtos we know all the files in the repo.
We also know the language each file belongs.

- we iterate over the list of files
- parse it with tree sitter: the parser used should depend on the language: for the time being Java, XML, Javascript
- traverse the AST top-down
- look at the size of the text range of the AST node, and go down until the size is lower than a threshold
- let's set the threshold to 512 chars: each node lower than the threshold is a chunk
- the output of the function should be the list of chunks for each file: the key is the URI of the file, the value is the list of chunks for that file, which is the start of the range and the end of the range in the source code of the file
- add unit tests to cover all important scenarios
- ensure 90% + coverage
- after all tests pass with coverage, please run a SonarQube analysis using the SonarQube MCP

----



- configurable chunk size
- chunks should overlap
- metadata: filename, being of range, end of range

...
FILE path
code before the chunk
chunk
code after the chunk
... (only if there is )

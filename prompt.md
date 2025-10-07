DONE

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

------------

DONE

There is another possibility from IntelliJ: use the PSI. Does it work for multiple languages? Can be extracted as a standalone library so that we don't depend on IntelliJ. We want this chunking to be working in any context: e.g. VSCode, not just IntelliJ.
Pros and Cons of this approach vs TreeSitter

------------

DONE

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

------------

We want to extend the chunking to be more "context-aware", and include 
- configurable chunk size with different strategies: 
  - largest ast node within chunk size limit (current strategy)
  - whole file (no chunk size limit, useful if we will calculate embedding using OpenAI, which does not have a chunk size limit)
- if you combine all chunks of a file, you should get back the entire file: we don't want parts of the files which are not covered by a chunk
- chunks should overlap, see the format below

# Chunk format
----
... (if the chunk is not at the beginning of the file)
code before the chunk
chunk
code after the chunk
... (if the chunk is not at the end fo the file )
----

The actual chunk needs to be shorter than the chunk threshold, because the chunk overall size needs to accomodate the code before and after, as well as the ellipses. What's important is that the overall size, everything included, is below the threshold.

Run all tests, and make sure that all tests related to chunking pass.
Fix all the issues, and keep running tests until it works.

------------

Let's extend the chunker to include some metadata in the chunk itself.
This will help the semantic search by giving contextual information.
For now, we want to add the file name, the chunk belongs to.
This metadata should be in the chunk, and should be at the beginning of the chunk itself.
Please keep in mind that the chunk size is quite small (e.g. 512 chars), and we don't want this contextual info to take too much space from the chunk: just "filename:" should be enough.
Also, the code before and after the chunk should be also not too long, compared to the actual content: you can pick a percentage of the entire chunk size (configurable, default 10%), and make sure that the code before and after the content respects that percentage.
And remember that the overall chunk size should not pass the threshold.


------------

Now that we have calulated chunks, each chunk should be sent to a remote server (written in Python) which performs:
- vectorization: takes the chunk and calculates embedding
- storage: stores the vectors in a vector DB

The body of the request for vectorizing a certain chunk should be a JSON:
```
{
    chunks: [
        {
            content: string // The content of the chunk
            filename: string // The URL of the file relative to the root of the repository/workspace
            metadata: {
                language: string // The language of the chunk (e.g. Java)
            }
            start_row: int // the starting row of the chunk in the source file
            start_column: int // the starting column of the chunk in the source file
            end_row: int
            end_column: int
        }
    ]
}
```

Each request should contain the chunk of a certain number of files. For example, if the number of files in the repo is 1000, and the number of files grouped together is 20, we would send 50 web requests to the server. Each web request would contain the chunks of 20 files.

response
```json
{

}
```

Can you run the maven tests related to chunking: something like -Dtest="**/chunking/**", and make sure they work?


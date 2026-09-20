# Vega Language Specification

**Version:** 0.1  
**Status:** Draft  
**Target:** JVM

> **Vega is a modern programming language designed for the JVM.**  
> Its syntax is inspired by Swift, Dart and Kotlin while maintaining its own identity.

---

# 1. Design Goals

Vega is designed around:

- Readable syntax
- Strong static typing
- Type inference
- Null safety
- Modern functions and collections
- Structs, classes and protocols
- Pattern matching
- Extensions
- Generics
- Structured concurrency
- First-class JVM interoperability
- Automatic memory management through the JVM
- A small and coherent language design

### Philosophy

> **Readable by default. Powerful when needed.**

---

# 2. Source Files

Vega source files use the `.vg` extension.

Example project:

```text
src/
├── main.vg
├── models/
│   └── user.vg
└── database/
    └── database.vg
```

---

# 3. Variables

## 3.1 Immutable Variables

`let` declares an immutable value.

```text
let name: String = "Ömer"
let age: Int = 13
```

A `let` value cannot be reassigned after initialization.

## 3.2 Mutable Variables

`var` declares a mutable value.

```text
var score: Int = 0

score = score + 10
```

## 3.3 Type Inference

Explicit types are optional when the compiler can determine them.

```text
let name = "Vega"
let age = 13
let active = true
```

The compiler infers:

```text
String
Int
Bool
```

---

# 3. Basic Types

The initial Vega type system contains:

```text
Int
Float
Bool
String
Char
Void
Any
```

Examples:

```text
let age: Int = 13
let pi: Float = 3.14159
let enabled: Bool = true
let name: String = "Vega"
let grade: Char = 'A'
```

`Void` represents the absence of a meaningful return value.

`Any` represents a value whose concrete type is not statically known.

---

# 4. Functions

Functions use the `fn` keyword.

```text
fn add(a: Int, b: Int) -> Int {
    a + b
}
```

The final expression is implicitly returned.

Explicit returns are also allowed:

```text
fn divide(a: Int, b: Int) -> Int {
    if b == 0 {
        return 0
    }

    a / b
}
```

---

# 5. String Interpolation

Expressions can be embedded inside strings.

```text
let name = "Ömer"
let age = 13

print("Hello, {name}! You are {age}.")
```

Expressions are supported:

```text
print("Next year: {age + 1}")
```

---

# 6. Structs

Structs are value types.

```text
struct User {
    let name: String
    let age: Int
}
```

Construction:

```text
let user = User(
    name: "Ömer",
    age: 13
)
```

Property access:

```text
print(user.name)
```

Structs receive a generated memberwise constructor unless a custom constructor is defined.

---

# 7. Methods

Methods are functions declared inside a type.

```text
struct Person {
    let name: String
    let age: Int

    fn describe() -> String {
        "{name} is {age} years old."
    }
}
```

Usage:

```text
print(person.describe())
```

---

# 8. Classes

Classes are reference types.

```text
class Database {
    fn connect() {
        print("Connected")
    }
}
```

Usage:

```text
let db = Database()
db.connect()
```

Classes support inheritance.

```text
class Animal {
    fn speak() {
        print("...")
    }
}

class Dog extends Animal {
    override fn speak() {
        print("Woof")
    }
}
```

## 8.1 Multiple Constructors

A class may define more than one `init` constructor. Constructors must have
different parameter lists; the matching overload is selected at the call site.

```text
class User {
    let name: String
    let age: Int

    init(name: String) {
        self.name = name
        self.age = 0
    }

    init(name: String, age: Int) {
        self.name = name
        self.age = age
    }
}

let guest = User(name: "Guest")
let adult = User(name: "Ömer", age: 13)
```

Structs do not support class inheritance.

---

# 9. Constructors

Classes can define an `init` constructor.

```text
class Database {
    let path: String

    init(path: String) {
        self.path = path
    }
}
```

Usage:

```text
let db = Database(path: "data.db")
```

---

# 10. Enums

Enums define a finite set of named values.

```text
enum Status {
    Active
    Disabled
    Pending
}
```

Usage:

```text
let status = Status.Active
```

Payload-carrying enum variants are planned but their exact syntax remains provisional.

---

# 11. Pattern Matching

`match` is Vega's main pattern-matching construct.

```text
match status {
    Status.Active => print("User is active")
    Status.Disabled => print("User is disabled")
    Status.Pending => print("User is waiting")
}
```

Ranges:

```text
match age {
    0..12 => print("Child")
    13..17 => print("Teen")
    18..64 => print("Adult")
    _ => print("Senior")
}
```

`_` is the wildcard pattern.

`match` can also produce a value:

```text
let category = match age {
    0..12 => "Child"
    13..17 => "Teen"
    _ => "Adult"
}
```

---

# 12. Ranges

Inclusive range:

```text
0..10
```

Exclusive upper bound:

```text
0..<10
```

Therefore:

```text
0..10
```

contains `0` through `10`, while:

```text
0..<10
```

contains `0` through `9`.

---

# 13. Optionals and Null Safety

Optional types use `?`.

```text
let username: String? = null
```

Optional binding:

```text
if let username {
    print(username)
}
```

Null coalescing:

```text
let displayName = username ?? "Unknown"
```

Optional chaining:

```text
user?.profile?.name
```

The compiler must prevent unsafe access to nullable values.

---

# 14. Conditional Expressions

Normal conditional statement:

```text
if age >= 18 {
    print("Adult")
} else {
    print("Under 18")
}
```

`if` can also produce a value:

```text
let status = if age >= 18 {
    "Adult"
} else {
    "Under 18"
}
```

---

# 15. Loops

## 15.1 For

```text
for user in users {
    print(user.name)
}
```

Range iteration:

```text
for i in 0..10 {
    print(i)
}
```

## 15.2 While

```text
var count = 0

while count < 10 {
    print(count)
    count = count + 1
}
```

---

# 16. Collections

Arrays use `[T]`.

```text
let numbers: [Int] = [1, 2, 3, 4, 5]
```

Type inference:

```text
let names = ["Ömer", "Ali", "Mehmet"]
```

Dictionaries use `[K: V]`.

```text
let users = [
    "Ömer": 13,
    "Ali": 16
]
```

Dictionary literal syntax is provisional and will be finalized before parser stabilization.

---

# 17. Tuples

Tuples contain multiple values.

```text
let user = ("Ömer", 13)

print(user.0)
print(user.1)
```

Named tuples:

```text
let user = (
    name: "Ömer",
    age: 13
)

print(user.name)
```

---

# 18. Lambdas

Lambda expressions use braces.

```text
let square = { x -> x * x }

print(square(5))
```

Multiple parameters:

```text
let add: (Int, Int) -> Int = { a, b ->
    a + b
}
```

Collection operations:

```text
let doubled = numbers.map { x ->
    x * 2
}
```

---

# 19. Generics

Generic functions:

```text
fn first<T>(items: [T]) -> T? {
    if items.isEmpty {
        return null
    }

    items[0]
}
```

Generic types:

```text
struct Box<T> {
    let value: T
}

let box = Box(value: 42)
```

Generic constraints:

```text
fn save<T>(item: T)
    where T: Printable & Identifiable
{
    ...
}
```

---

# 20. Protocols

Protocols define shared behavior.

```text
protocol Printable {
    fn printInfo() -> String
}
```

A type can implement a protocol:

```text
struct User implements Printable {
    let name: String

    fn printInfo() -> String {
        "User: {name}"
    }
}
```

Multiple protocols:

```text
struct Admin implements Printable, Identifiable {
    ...
}
```

Protocols can provide default implementations:

```text
protocol Printable {
    fn printInfo() -> String

    fn print() {
        print(printInfo())
    }
}
```

Protocol composition:

```text
fn save<T>(item: T)
    where T: Printable & Identifiable
{
    ...
}
```

---

# 21. Extensions

Extensions add functionality to existing types.

```text
extension String {
    fn isBlank() -> Bool {
        ...
    }
}
```

Usage:

```text
let name = "Vega"

if name.isBlank() {
    ...
}
```

Extensions may also define computed properties.

The exact computed-property grammar remains provisional.

---

# 22. Operator Overloading

Vega supports operator overloading for a controlled set of operators.

Initial operators:

```text
+
-
*
/
%
==
!=
<
>
<=
>=
```

Example:

```text
struct Vector {
    let x: Float
    let y: Float

    operator +(a: Vector, b: Vector) -> Vector {
        Vector(
            x: a.x + b.x,
            y: a.y + b.y
        )
    }
}
```

Usage:

```text
let c = a + b
```

Custom user-defined operator symbols are not part of Vega 0.1.

---

# 23. Pipeline Operator

Vega defines `|>` as the pipeline operator.

```text
numbers
    |> filter { x -> x % 2 == 0 }
    |> map { x -> x * 2 }
    |> print
```

The pipeline operator has very low precedence.

---

# 24. Operator Precedence

Initial precedence from highest to lowest:

```text
*
/
%

+
-

<
>
<=
>=

==
!=

&&

||

|>
```

The exact parser precedence table will be frozen before compiler stabilization.

---

# 25. Error Handling

Functions may declare that they throw errors.

```text
fn loadUser(id: Int) -> User throws {
    ...
}
```

Typed errors:

```text
error UserNotFound {
    id: Int
}
```

Throwing:

```text
throw UserNotFound(id: 42)
```

Handling:

```text
try {
    let user = loadUser(42)
    print(user.name)
} catch error {
    print("Error: {error}")
}
```

---

# 26. Result

Vega also supports explicit result-based error handling.

```text
fn loadUser(id: Int) -> Result<User, UserError> {
    ...
}
```

Handling:

```text
match loadUser(42) {
    Ok(user) => print(user.name)
    Err(error) => print(error)
}
```

This allows both exception-based and result-based error handling.

---

# 27. Defer

`defer` executes a block when the surrounding function scope exits.

```text
fn readFile() {
    let file = open("data.txt")

    defer {
        file.close()
    }

    ...
}
```

The JVM backend may implement this using `try/finally` semantics.

---

# 28. Access Modifiers

Vega supports:

```text
public
private
protected
internal
```

Default visibility:

```text
internal
```

Example:

```text
class Database {
    private let connection: Connection

    public fn query(sql: String) {
        ...
    }
}
```

---

# 29. Modules

Modules organize source files.

Example:

```text
module models.user
```

Import:

```text
import models.user
```

Specific symbol:

```text
import models.user.User
```

Alias:

```text
import models.user.User as Person
```

Wildcard imports are intentionally excluded from Vega 0.1.

---

# 30. Packages

Packages provide namespace organization compatible with the JVM.

```text
package com.omerkerim.vegaapp
```

The compiler maps Vega packages to JVM packages.

---

# 31. Annotations

Annotations provide metadata.

```text
@Deprecated
fn oldFunction() {
    ...
}
```

Java annotation:

```text
@java.lang.Deprecated
fn oldFunction() {
    ...
}
```

Custom annotation:

```text
annotation Serializable
```

Usage:

```text
@Serializable
struct User {
    let name: String
}
```

---

# 32. Async Programming

Vega uses `async` and `await`.

```text
async fn loadUser(id: Int) -> User {
    ...
}

fn main() async {
    let user = await loadUser(42)

    print(user.name)
}
```

Async functions conceptually produce `Task<T>`.

---

# 33. Tasks

Tasks can be created with `spawn`.

```text
let task = spawn {
    calculateSomething()
}

let result = await task
```

`spawn` does not necessarily create a new operating-system thread.

The runtime may use a scheduler or worker pool.

---

# 34. Parallelism

Vega may explicitly represent parallel work:

```text
let (a, b) = parallel {
    calculateA()
    calculateB()
}
```

The runtime determines how the work is scheduled.

The exact semantics of `parallel` are provisional.

---

# 35. Channels

Channels provide message-based communication.

```text
let channel = Channel<Int>()

await channel.send(42)

let value = await channel.receive()
```

Channels are intended to reduce shared mutable state.

---

# 36. Actors

Actors provide isolated mutable state.

```text
actor Counter {
    var value: Int = 0

    fn increment() {
        value = value + 1
    }

    fn get() -> Int {
        value
    }
}
```

Usage:

```text
let counter = Counter()

await counter.increment()

let value = await counter.get()
```

Actor state cannot be accessed directly from unrelated concurrent contexts.

---

# 37. Async Streams

Planned async stream syntax:

```text
async fn numbers() -> Stream<Int> {
    yield 1
    yield 2
    yield 3
}
```

Consumption:

```text
for await value in numbers() {
    print(value)
}
```

This feature is planned but not required for the first compiler prototype.

---

# 38. Memory Model

Vega uses the JVM garbage collector.

There is no manual memory management:

```text
malloc
free
delete
```

are not part of the language.

Initial semantic model:

```text
struct  -> value semantics
class   -> reference semantics
actor   -> isolated reference semantics
```

Object lifetime is managed by the JVM.

---

# 39. JVM Interoperability

JVM interoperability is a first-class Vega feature.

Java classes can be imported directly.

```text
import java.util.ArrayList

fn main() {
    let list = ArrayList<String>()

    list.add("Vega")
    list.add("JVM")

    print(list.get(0))
}
```

Java libraries should not require wrapper classes merely to be used from Vega.

---

# 40. Java APIs

Example:

```text
import java.nio.file.Files
import java.nio.file.Path

let text = Files.readString(Path.of("data.txt"))
```

The compiler generates the corresponding JVM method invocation.

---

# 41. Java Generics

Java generic APIs are accessible from Vega.

Conceptually:

```text
List<String>
Map<String, Int>
```

The compiler handles JVM generic representation and boxing/unboxing where required.

---

# 42. Java Lambdas

Java functional interfaces should be usable through Vega lambdas.

Example:

```text
list.removeIf { x ->
    x.isEmpty()
}
```

The compiler generates the appropriate JVM functional-interface representation.

---

# 43. Vega-to-Java Interoperability

Vega programs compile to standard JVM artifacts.

```text
Vega source
    ↓
Vega compiler
    ↓
.class / .jar
    ↓
Java
```

Java programs should be able to use public Vega classes and methods.

---

# 44. JVM Ecosystem

Java interoperability is the official compatibility target.

Because Kotlin and Scala also target the JVM, many libraries written in those languages may be usable when their APIs are exposed through compatible JVM bytecode.

---

# 45. JVM Primitive Mapping

Initial conceptual mapping:

```text
Vega Int    -> JVM int
Vega Float  -> JVM float
Vega Bool   -> JVM boolean
Vega Char   -> JVM char
```

Boxing and unboxing are compiler responsibilities.

The exact numeric ABI is provisional.

---

# 46. Vega Runtime

Vega may ship with:

```text
vega-runtime.jar
```

Potential runtime components:

```text
VegaTask
Channel
Actor
Result
collection helpers
runtime utilities
```

The runtime should remain small and avoid unnecessarily duplicating the Java standard library.

---

# 47. Standard Library

Planned modules:

```text
vega.io
vega.collections
vega.math
vega.json
vega.fs
vega.net
vega.concurrent
```

The standard library should provide convenient Vega APIs while remaining interoperable with JVM libraries.

---

# 48. Serialization

Annotations can support serialization.

```text
@Serializable
struct User {
    let name: String
    let age: Int
}
```

Potential API:

```text
let json = Json.encode(user)
```

The final serialization API is not yet frozen.

---

# 49. Build System

Planned commands:

```bash
vega build
vega run
vega test
vega package
```

The build system will eventually handle:

- Source modules
- Packages
- Dependencies
- JVM target
- Compiler version
- Runtime version
- Tests

---

# 50. Package Manager

A future package manager may support:

```bash
vega add http
vega add json
```

Dependency repositories and package format are provisional.

---

# 51. Compiler Architecture

The planned compiler pipeline:

```text
Vega source
    ↓
Lexer
    ↓
Parser
    ↓
AST
    ↓
Name resolution
    ↓
Type checking
    ↓
Semantic analysis
    ↓
Lowering
    ↓
JVM bytecode generation
    ↓
.class / .jar
```

Compiler errors should be reported using Vega source locations and understandable messages.

---

# 52. JVM Backend

The initial backend targets JVM bytecode.

Example:

```text
main.vg
    ↓
Vega compiler
    ↓
Main.class
    ↓
JVM
```

Applications can eventually be packaged as:

```text
application.jar
```

---

# 53. Example Vega Program

```text
package com.omerkerim.app

import vega.io

@Serializable
struct User implements Printable {
    let name: String
    let age: Int

    fn printInfo() -> String {
        "{name} ({age})"
    }
}

extension User {
    fn isAdult() -> Bool {
        age >= 18
    }
}

fn classify(user: User) -> String {
    match user.age {
        0..12 => "Child"
        13..17 => "Teen"
        18..64 => "Adult"
        _ => "Senior"
    }
}

fn main() {
    let users = [
        User(name: "Ömer", age: 13),
        User(name: "Ali", age: 20)
    ]

    users
        |> filter { user -> user.isAdult() }
        |> map { user -> user.name }
        |> print
}
```

---

# 54. Design Influences

Vega takes inspiration from:

### Swift

- Value types
- Optionals
- Pattern matching
- Extensions
- Modern syntax

### Kotlin

- Null safety
- JVM ecosystem
- Concise syntax
- Smart type system

### Dart

- Approachable syntax
- Async programming
- Collection APIs
- Productive development experience

Vega is not intended to be a clone of any of these languages.

---

# 55. Vega Identity

Vega aims to combine:

```text
Readable syntax
        +
Strong static typing
        +
Modern type system
        +
Safe concurrency
        +
JVM ecosystem
```

The language should feel modern without becoming syntactically complicated.

---

# 56. Provisional Features

The following areas require final decisions before Vega 0.1 becomes frozen:

- Dictionary literal grammar
- Enum payload syntax
- Computed-property syntax
- Exact operator precedence table
- `parallel` semantics
- Async stream semantics
- Numeric primitive widths
- JVM ABI
- Package manager format
- Serialization API
- Complete standard library
- Generic variance
- Reflection API
- Java annotation mapping
- Full Java interop rules

---

# 57. Compiler Roadmap

## Phase 1 — Lexer

Support:

```text
identifiers
keywords
numbers
strings
characters
operators
comments
```

## Phase 2 — Parser

Support:

```text
let
var
functions
expressions
if / else
loops
structs
function calls
```

## Phase 3 — Type Checker

Support:

```text
Int
Float
Bool
String
Char
Void
arrays
functions
type inference
```

## Phase 4 — JVM Backend

Generate `.class` files.

The first successful program should be:

```text
print("Hello, Vega!")
```

## Phase 5 — Advanced Types

Add:

```text
struct
class
enum
protocol
generics
optionals
match
extensions
annotations
```

## Phase 6 — Runtime

Add:

```text
Task
async / await
Channel
Actor
Result
```

## Phase 7 — Tooling

Add:

```text
vega build
vega run
vega test
vega package
```

---

# 58. Status

**Vega 0.1 is currently a design draft.**

The syntax described here represents the current intended direction of the language.

Features explicitly marked as provisional should be finalized before the compiler API and public syntax are considered stable.

The long-term goal is a modern, enjoyable language capable of producing first-class JVM applications.

---

# Vega

> **Modern syntax. JVM power.**

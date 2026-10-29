# A ternary of two ternaries of class literals is cast to the classes' common supertype under invokedynamic

Since Groovy 5.0.0-alpha-4 (GROOVY-10936), the type of a ternary expression is the common supertype of its branches'
types. A class literal's type is the class it names, not `java.lang.Class`, so a ternary that chooses between two
ternaries of class literals,

```groovy
Class<?> choose(boolean a, boolean b, boolean c) {
    a ? (b ? First : Second) : (c ? Third : Fourth)
}
```

is typed as the classes' common supertype, `Marker`. The chosen `Class` object is then converted *from* `Marker`
wherever it is used: returned, passed as an argument, or assigned to a typed variable. With invokedynamic that
conversion is a `cast` call site declared as `(Marker)Class`, and linking it casts the `Class` object to `Marker`:

```
java.lang.ClassCastException: Cannot cast java.lang.Class to Marker
```

Groovy 4, and Groovy 5 and 6 with classic call sites, return the chosen class. A single-level ternary of class
literals, `a ? First : Second`, is unaffected, because the bytecode writer resolves a class literal operand to
`Class<First>` itself; a ternary's own type is consulted only when it is an operand of another ternary. A Grails
application moving from `grails { indy = false }` to invokedynamic (the Grails 9 default) meets this in code that
picks a class by conditions.

## Reproducer

`NestedTernaryOfClassLiterals.groovy` has no dependencies. `Chooser.choose()` returns the chosen class and
`Chooser.nameOf()` passes it to a method that takes a `Class<?>`:

```
$ ./run.sh 4.0.33 5.1.3 6.0.0
== Groovy 4.0.33
   indy=true: nested ternary of class literals returned: Third (OK)
   indy=true: nested ternary of class literals passed as an argument: Third (OK)
   indy=false: nested ternary of class literals returned: Third (OK)
   indy=false: nested ternary of class literals passed as an argument: Third (OK)
== Groovy 5.1.3
   indy=true: nested ternary of class literals returned: ClassCastException: Cannot cast java.lang.Class to Marker (FAIL)
   indy=true: nested ternary of class literals passed as an argument: ClassCastException: Cannot cast java.lang.Class to Marker (FAIL)
   indy=false: nested ternary of class literals returned: Third (OK)
   indy=false: nested ternary of class literals passed as an argument: Third (OK)
== Groovy 6.0.0
   indy=true: nested ternary of class literals returned: ClassCastException: Cannot cast java.lang.Class to Marker (FAIL)
   indy=true: nested ternary of class literals passed as an argument: ClassCastException: Cannot cast java.lang.Class to Marker (FAIL)
   indy=false: nested ternary of class literals returned: Third (OK)
   indy=false: nested ternary of class literals passed as an argument: Third (OK)
```

The jars are looked up in `~/.m2/repository` and the Gradle cache. Classic call sites on Groovy 6 need
`groovy-callsite`.

## Cause

`TernaryExpression.getType()` returns `WideningCategories.lowestUpperBound()` of its branches' `getType()`
(GROOVY-10936), and `ClassExpression.getType()` is the class the literal names.
`BinaryExpressionHelper.evaluateTernaryExpression()` types each branch with `StatementMetaTypeChooser.resolveType()`,
which answers `Class<T>` for a class expression but `getType()` for a nested ternary. So the inner ternaries are typed
`Marker`, the outer ternary's common type is `Marker`, and the operand stack carries `Marker` for a value that is a
`Class`. The conversion to `Class` is then written as `invokedynamic cast:(LMarker;)Ljava/lang/Class;`, whose method
type makes the JVM cast the argument to `Marker` before the call site sees it. Classic call sites convert the value to
`Class` directly and accept it.

## Suggested fix

In `TernaryExpression.getType()`, give a class literal operand the type `Class<T>`, as
`StatementMetaTypeChooser.resolveType()` does, so a ternary of class literals is typed `Class` wherever it appears.

## Environment

JDK 25.0.4 (Liberica), macOS. Reproduced on Groovy 5.1.3 and 6.0.0 with invokedynamic. Not reproduced on 4.0.33, or on
5.1.3 and 6.0.0 with classic call sites.

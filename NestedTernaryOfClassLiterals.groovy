// A ternary that chooses between two ternaries of class literals. Each class literal evaluates to a java.lang.Class,
// so the whole expression does too. Since GROOVY-10936 (5.0.0-alpha-4) the type of a ternary is the common supertype
// of its branches' types, and a class literal's type is the class it names, so the inner ternaries are typed Marker
// and the outer ternary casts the chosen Class object to Marker.
interface Marker {}
class First implements Marker {}
class Second implements Marker {}
class Third implements Marker {}
class Fourth implements Marker {}

class Chooser {
    Class<?> choose(boolean a, boolean b, boolean c) {
        a ? (b ? First : Second) : (c ? Third : Fourth)
    }

    String nameOf(boolean a, boolean b, boolean c) {
        name(a ? (b ? First : Second) : (c ? Third : Fourth))
    }

    private static String name(Class<?> type) { type.simpleName }
}

String mode = "indy=${System.getProperty('groovy.target.indy', 'true (default)')}"
def chooser = new Chooser()
for (step in [[' returned', { chooser.choose(false, true, true) }], [' passed as an argument', { chooser.nameOf(false, true, true) }]]) {
    try {
        def result = step[1]()
        println "${mode}: nested ternary of class literals${step[0]}: ${result instanceof Class ? result.simpleName : result} (OK)"
    } catch (ClassCastException | org.codehaus.groovy.runtime.typehandling.GroovyCastException e) {
        println "${mode}: nested ternary of class literals${step[0]}: ${e.class.simpleName}: ${e.message} (FAIL)"
    }
}

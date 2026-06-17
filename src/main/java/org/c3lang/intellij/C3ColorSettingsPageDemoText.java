package org.c3lang.intellij;

public final class C3ColorSettingsPageDemoText
{
	public static final String DEMO_TEXT = """
module stack <Type>;
// Above: the parameterized type is applied to the entire module.

faultdef FILE_NOT_FOUND, FILE_IS_DIR;

/* A block comment. */

<*
 Pushes a greeting and reports the first fault.
 @param msg : "the greeting to print"
*>
fn void fault_example(String msg)\s
{
    io::printfn("Hello\\t%s\\n", msg);
    return FILE_NOT_FOUND?;
}

struct Stack
{
    usz capacity;
    usz size;
    Type* elems;
}

fn void Stack.push(Stack* this, Type element)
{
    if (this.capacity == this.size)
    {
        this.capacity *= 2;
		if (this.capacity < 16) this.capacity = 16;
        this.elems = mem::realloc(this.elems, Type.sizeof * this.capacity);
    }
    this.elems[this.size++] = element;
}

fn Type Stack.pop(Stack* this)
{
    assert(this.size > 0);
    return this.elems[--this.size];
}

macro bool Stack.empty(Stack* this)
{
    return !this.size;
}
""";

	private C3ColorSettingsPageDemoText() {}
}

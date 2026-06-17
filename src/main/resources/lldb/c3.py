"""LLDB formatters for C3 types (bundled with the C3 IntelliJ plugin).

Imported into the LLDB session by C3DebugProcessConfigurator via
``command script import``. ``__lldb_init_module`` registers the summaries.
"""

import lldb


def String_Summary(valobj, internal_dict):
    # Read the ptr + len fields of a C3 String (a slice over char data).
    ptr_val = valobj.GetChildMemberWithName("ptr").GetValueAsUnsigned(0)
    len_val = valobj.GetChildMemberWithName("len").GetValueAsUnsigned(0)

    if len_val == 0:
        return '""'

    error = lldb.SBError()
    process = valobj.GetProcess()
    data = process.ReadMemory(ptr_val, len_val, error)

    if error.Success():
        return f'"{data.decode("utf-8", "replace")}"'
    else:
        return f'<error reading memory: {error.GetCString()}>'


def __lldb_init_module(debugger, internal_dict):
    # Register the summary against the C3 `String` type. The -x flag treats the
    # argument as a regular expression so module-qualified names also match.
    debugger.HandleCommand(
        'type summary add -F {0}.String_Summary -x "String$"'.format(__name__)
    )

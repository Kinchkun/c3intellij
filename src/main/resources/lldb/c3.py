"""LLDB formatters for C3 types (bundled with the C3 IntelliJ plugin).

Imported into the LLDB session by C3DebugProcessConfigurator via
``command script import``. ``__lldb_init_module`` registers the formatters.

A C3 ``String`` is a fat pointer ``{ char* ptr; usz len; }`` and is *not*
null-terminated. The default ``char*`` rendering therefore reads past the end
and shows trailing garbage. The summary below reads exactly ``len`` bytes, and
the synthetic provider replaces the raw ``char*`` child with a ``char[len]``
view so the expanded value stays bounded too.
"""

import lldb


def _raw(valobj):
    # Read the underlying struct fields even when a synthetic provider is
    # registered for the same type (which would otherwise hide ptr/len).
    nonsynth = valobj.GetNonSyntheticValue()
    return nonsynth if nonsynth and nonsynth.IsValid() else valobj


def _read_chars(valobj):
    raw = _raw(valobj)
    ptr = raw.GetChildMemberWithName("ptr")
    length = raw.GetChildMemberWithName("len")
    if not ptr.IsValid() or not length.IsValid():
        return None

    ptr_val = ptr.GetValueAsUnsigned(0)
    len_val = length.GetValueAsUnsigned(0)
    if ptr_val == 0 or len_val == 0:
        return ""

    error = lldb.SBError()
    data = valobj.GetProcess().ReadMemory(ptr_val, len_val, error)
    if not error.Success() or data is None:
        return None
    return data.decode("utf-8", "replace")


def String_Summary(valobj, internal_dict):
    chars = _read_chars(valobj)
    if chars is None:
        return "<unreadable C3 String>"
    return '"' + chars.replace("\\", "\\\\").replace('"', '\\"') + '"'


class String_SyntheticProvider:
    """Presents a C3 String as ``len`` plus a length-bounded ``chars`` array,
    hiding the raw null-terminated ``char*`` that overruns the real content."""

    def __init__(self, valobj, internal_dict):
        self.valobj = valobj
        self.ptr = None
        self.len_field = None
        self.length = 0

    def update(self):
        raw = _raw(self.valobj)
        self.ptr = raw.GetChildMemberWithName("ptr")
        self.len_field = raw.GetChildMemberWithName("len")
        self.length = self.len_field.GetValueAsUnsigned(0) if self.len_field.IsValid() else 0
        return False

    def has_children(self):
        return True

    def num_children(self):
        return 2

    def get_child_index(self, name):
        if name == "len":
            return 0
        if name == "chars":
            return 1
        return -1

    def get_child_at_index(self, index):
        try:
            if index == 0:
                return self.len_field
            if index == 1 and self.ptr is not None and self.ptr.IsValid():
                char_type = self.ptr.GetType().GetPointeeType()
                array_type = char_type.GetArrayType(self.length)
                return self.valobj.CreateValueFromAddress(
                    "chars", self.ptr.GetValueAsUnsigned(0), array_type)
        except Exception:
            pass
        return None


def __lldb_init_module(debugger, internal_dict):
    module = __name__
    # -x: the type-name argument is a regular expression, so module-qualified
    # names (e.g. std::core::String) match the same formatter.
    debugger.HandleCommand(
        'type summary add -F {0}.String_Summary -x "String$"'.format(module))
    debugger.HandleCommand(
        'type synthetic add -l {0}.String_SyntheticProvider -x "String$"'.format(module))

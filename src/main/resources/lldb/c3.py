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


# --------------------------------------------------------------------------- #
# HashMap
#
# std::collections::map::HashMap{K, V} is a chained hash map:
#   { Entry{K,V}*[] table; Allocator allocator; int count; int threshold; ... }
# where the slice ``table`` is ``{ Entry** ptr; usz len }`` (len = bucket count)
# and each ``Entry`` is ``{ uint hash; K key; V value; Entry* next }``. The live
# key/value pairs are spread across the buckets and their ``next`` chains, so the
# raw view only shows the bucket array. The provider below walks every chain and
# presents one child per entry, named by the key (for String keys) -> value.
# --------------------------------------------------------------------------- #

_HASHMAP_CHAIN_GUARD = 1 << 20  # defensive cap against a corrupt/cyclic chain


def _hashmap_entries(valobj):
    """All live ``Entry`` SBValues, walking every bucket and collision chain."""
    raw = _raw(valobj)
    table = raw.GetChildMemberWithName("table")
    if not table.IsValid():
        return []
    table = _raw(table)
    ptr = table.GetChildMemberWithName("ptr")     # Entry**
    length = table.GetChildMemberWithName("len")  # bucket count
    if not ptr.IsValid() or not length.IsValid():
        return []
    if ptr.GetValueAsUnsigned(0) == 0:
        return []

    entries = []
    nbuckets = length.GetValueAsUnsigned(0)
    for i in range(nbuckets):
        slot = ptr.GetChildAtIndex(i, lldb.eNoDynamicValues, True)  # ptr[i] : Entry*
        if not slot.IsValid():
            continue
        guard = 0
        while slot.GetValueAsUnsigned(0) != 0 and guard < _HASHMAP_CHAIN_GUARD:
            entry = slot.Dereference()  # Entry
            if not entry.IsValid():
                break
            entries.append(entry)
            slot = entry.GetChildMemberWithName("next")  # Entry*
            if not slot.IsValid():
                break
            guard += 1
    return entries


def _entry_label(entry, fallback):
    key = entry.GetChildMemberWithName("key")
    if key.IsValid():
        chars = _read_chars(key)  # non-None only for String-shaped keys
        if chars is not None:
            return '["' + chars.replace('"', '\\"') + '"]'
    return fallback


def HashMap_Summary(valobj, internal_dict):
    raw = _raw(valobj)
    count = raw.GetChildMemberWithName("count")
    n = count.GetValueAsUnsigned(0) if count.IsValid() else len(_hashmap_entries(valobj))
    return "size=" + str(n)


class HashMap_SyntheticProvider:
    """Presents a HashMap as one child per live entry (key -> value), instead of
    the raw bucket array."""

    def __init__(self, valobj, internal_dict):
        self.valobj = valobj
        self.entries = []

    def update(self):
        try:
            self.entries = _hashmap_entries(self.valobj)
        except Exception:
            self.entries = []
        return False

    def has_children(self):
        return True

    def num_children(self):
        return len(self.entries)

    def get_child_index(self, name):
        return -1

    def get_child_at_index(self, index):
        try:
            if index < 0 or index >= len(self.entries):
                return None
            entry = self.entries[index]
            value = entry.GetChildMemberWithName("value")
            label = _entry_label(entry, "[" + str(index) + "]")
            if value.IsValid():
                addr = value.GetLoadAddress()
                if addr != lldb.LLDB_INVALID_ADDRESS:
                    # Re-label the value with the key so the row reads key -> value.
                    return self.valobj.CreateValueFromAddress(label, addr, value.GetType())
                return value
            return entry
        except Exception:
            return None


def __lldb_init_module(debugger, internal_dict):
    module = __name__
    # -x: the type-name argument is a regular expression, so module-qualified
    # names (e.g. std::core::String) match the same formatter.
    debugger.HandleCommand(
        'type summary add -F {0}.String_Summary -x "String$"'.format(module))
    debugger.HandleCommand(
        'type synthetic add -l {0}.String_SyntheticProvider -x "String$"'.format(module))

    # HashMap{K, V}: the generic instantiation name is e.g. "HashMap{String, String}".
    # Matching the canonical type also covers `alias`/typedef names (e.g. `Matches`),
    # since LLDB cascades a formatter to typedefs of the matched type.
    hashmap_re = r'HashMap\{'
    debugger.HandleCommand(
        'type summary add -F {0}.HashMap_Summary -x "{1}"'.format(module, hashmap_re))
    debugger.HandleCommand(
        'type synthetic add -l {0}.HashMap_SyntheticProvider -x "{1}"'.format(module, hashmap_re))

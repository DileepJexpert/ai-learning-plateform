# Module 5 — Tool Calling / Function Calling / Agents

**Build:** Give the LLM tools (`get_customer`, `query_ledger`), let it decide which
to call, execute them in Spring Boot, feed the results back, and loop until it
produces a final answer — with validation + retry for malformed tool calls. Done —
see the `agent` package.

> Run it: `POST /api/agent/ask {"question":"..."}` with a tool-capable model
> (qwen2.5-coder:7b). `GET /api/agent/tools` lists what's available.

---

## The concept in my own words

### Function calling under the hood

An LLM can't actually *do* anything — it only emits text. "Function calling" is a
protocol that lets it emit a **structured request to call your code**:

1. In the chat request I send a `tools` array: each tool's **name**, a
   **description** (the model's only clue about when to use it), and a **JSON
   Schema** for its arguments.
2. Instead of normal text, the model can reply with a `tool_calls` array:
   `{"name": "get_customer", "arguments": {"id": "C-100"}}`.
3. **I** execute the real function in Spring Boot (hit a DB, call a service…).
4. I append the result as a `tool`-role message and call the model again.
5. The model reads the result and either calls another tool or writes the final
   answer.

The model never runs code — it just *picks* a function and *fills in* arguments.
The execution, security, and correctness are entirely my responsibility. That
boundary is the whole interview answer to "how does function calling work?".

### The agentic loop (ReAct)

Steps 2–5 repeat. That loop **is** the agent:

```
        ┌────────────────────────────────────────────┐
        ▼                                            │
   ask the model ──► tool_calls? ──no──► final answer ✓
   (with tools)          │ yes
        ▲                ▼
        │         execute tool(s) in Spring Boot
        │                │
        └──── append tool result(s) ◄──┘     (capped at maxIterations)
```

This is the **ReAct** pattern — *Reason* (model thinks about what it needs),
*Act* (calls a tool), *Observe* (reads the result), repeat. An "agent" is, at the
core, just this loop plus a set of tools. There's no magic — it's an orchestration
loop I own in Spring Boot.

### Validating & retrying hallucinated tool calls

Local models *will* misbehave: call a tool that doesn't exist, omit a required
argument, or pass a bad value. The robust pattern is **don't throw — feed the
error back as the tool result** so the model self-corrects on the next turn. This
project handles three failure modes that way:

| Failure | What we return to the model |
|---------|------------------------------|
| Unknown tool | `Error: unknown tool 'frobnicate'. Available tools: [get_customer, query_ledger]` |
| Missing required arg | `Error: missing required argument(s) [id] for tool 'get_customer'.` |
| Execution error (bad id) | `Error: No customer with id 'C-999'. Known customer ids: [C-100, C-200, C-300]` |

The error messages are **actionable** (they list valid options), so the model can
recover — exactly the same "feed the failure back and let it fix itself"
philosophy as Module 1's auto-retry. Plus a hard **iteration cap** so a confused
model can't loop forever.

### Which models support tool calling?

Not all do — the model must be trained for it. Locally: **Qwen2.5** (used here),
**Llama 3.1+**, and Mistral support tools. Smaller/older models (e.g. base
llama3.2:3b) may ignore the `tools` field or format calls badly — another reason
the validation layer matters.

---

## What's in the box

- **`Tool`** — interface: name, description, JSON-Schema parameters, `execute()`.
  A tool is just a Spring bean.
- **`GetCustomerTool` / `QueryLedgerTool`** — real implementations over an
  in-memory `ErpDataStore` (stands in for Postgres / a downstream service).
- **`ToolRegistry`** — Spring injects *all* `Tool` beans, so adding a capability
  is a one-class change. Builds the wire-format definitions and looks tools up by name.
- **`AgentService`** — the loop: advertise tools → execute calls → feed results
  back → stop on final answer or the iteration cap.
- **`AgentResult`** — the answer **plus a full trace** of every tool call (name,
  args, result, ok/error). The trace is what makes the loop observable.

---

## Interview angles

> **"How do AI agents work?"**

An agent is an orchestration loop around an LLM that can call tools. I give the
model a set of tool schemas; it replies either with a final answer or a request
to call a tool; I execute that tool in my code, feed the result back, and loop
(the ReAct pattern: reason → act → observe). I add validation so malformed calls
are returned as errors the model can recover from, and a hard iteration cap for
safety. The model decides *what* to do; my service controls *execution*.

> **"How does function calling work under the hood?"**

It's a structured-output protocol. I describe functions (name + description +
JSON-Schema params) in the request; the model emits a `tool_calls` object naming
a function and its arguments instead of free text; I parse and execute it, then
return the result as a tool-role message for the next turn. The model never runs
code — it only selects and parameterises functions. Everything about *doing* the
work, and doing it safely, is on my side.

> **"What stops it going wrong?"**

Validate every tool call (known tool? required args present? value valid?) and
feed failures back as actionable errors instead of crashing, so the model
adapts. Cap the loop iterations. Keep tools narrow and least-privilege. Log the
full trace for audit. (Prompt-injection defence and PII handling — Module 6 —
matter a lot once tools can touch real systems.)

---

### Resume line
> *"Built a tool-calling agent (Spring Boot + Ollama, Qwen2.5) with an auto-discovered
> tool registry, a ReAct execution loop, and validation that feeds malformed tool
> calls back to the model for self-correction — plus an iteration cap and a full
> call trace for observability."*

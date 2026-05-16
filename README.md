📱 Sketchware Pro — Complete Engineering Reference
Developer's Master Guide | GitHub Edition | Modified Version
Level: Senior Android IDE + AI Systems Architecture
Current State: Modified version — Phase 0→5 complete
Authority: This file is the single source of truth for the project.
Table of Contents
Project Identity
Technical Architecture
The Golden Rule
Current Version State
Completed Fixes — Phase by Phase
Every Screen: Description + Issues + Fixes + Proposals
AI System — Tools, Pipelines, Prompts
Block System
Build System
Performance & Memory
Security
UI/UX & Material 3
AI Providers
Future Development Roadmap
Immutable Development Rules
1. Project Identity
What is Sketchware Pro?
Sketchware Pro is not an ordinary app. It is:
A complete Android development environment running inside Android itself
A visual programming editor built on a Block Graph, not just text files
A dynamic Java/XML generator at runtime
An integrated build system (ECJ → D8 → R8 → APK packaging)
A full mobile IDE with a fully authorized AI Agent
The Fundamental Difference from Other Apps
Ordinary App
Sketchware Pro
Text files only
Graph-based project model
Prompt → Response
Orchestrated tool pipeline
Context = text
Block graph + XML + Java AST
Edit a file
Incremental patch on a live graph
Core Technical Specs
Code
2. Technical Architecture
Code Structure
Code
Project Data Storage Format
Code
3. The Golden Rule
Sketchware is a Mobile Android IDE first, an AI Platform second.
If the IDE itself freezes, leaks RAM, or produces unstable builds,
no amount of AI capability will save it.
Stability first → Performance second → AI third
4. Current Version State
What Has Been Accomplished (Phase 0 → Phase 5)
This GitHub version represents the state of the project after 5 phases of fixes and improvements.
✅ Fixed and Working
Operator blocks — all 43+ blocks in the Operator category render correctly with input slots
String/Math blocks — all palette blocks show proper spec (label + input slots)
AirForce AI — replaces Chutes AI (URL: api.airforce, completely free, no API key)
LLM LOCAL — improved (correct Ollama port, better timeouts, clear error messages)
Morph LLM — URL updated: morphllm.com/dashboard/api-keys
Design Editor bottom bar — undo/redo removed, page name enlarged, Run kept
AI providers BottomSheet — providers show models even before Refresh
deleteProject ANR — immediate removal from shownProjects with notifyItemRemoved
backupProject ANR — moved to background thread
addProject — targeted update with notifyItemInserted(0)
AiProviderAdapter — Get Key for all 22 providers, Visit Website for free providers
AiErrorHelper — clearer 429 messages, removed incorrect "AirForce" reference
System Prompt — comprehensive update via SketchwareAiPipeline.PIPELINE_SYSTEM_PROMPT
⚠️ Present but Needs Development
Design Editor — Drag & Drop, XML preview, full undo/redo
Main Screen ANR — partial fix, full ProjectOperationsManager still needed
Block Rendering — viewport-only rendering not yet implemented
AI tools pipeline — SketchwareAiPipeline.java exists but full integration is Phase 6
❌ Not Started (Future Phases)
Edge-to-Edge fix (blocked due to incorrect description in original reports)
Context Compressor for AI
XML Patch Engine
ID Lock Layer
Project Indexer
Full DiffUtil in ProjectsAdapter
5. Completed Fixes
Phase 0 — Operator Blocks, LLM LOCAL, Morph
Files modified:
java/a/a/a/Fx.java — block code generator
java/pro/sketchware/ai/models/AiProvider.java
java/pro/sketchware/ai/models/AiProviderModels.java
java/pro/sketchware/ai/activities/AiSettingsActivity.java
java/pro/sketchware/ai/api/LocalLlmApiClient.java
java/pro/sketchware/ai/storage/AiPreferences.java
res/layout/activity_ai_settings.xml
Changes:
Fix
File
Detail
CRITICAL-02a: divide by zero
Fx.java
(b != 0 ? (double)a/b : 0.0)
CRITICAL-02b: modulo by zero
Fx.java
(b != 0 ? a%b : 0)
CRITICAL-02c: null in strings
Fx.java
guard on length/join/contains
CRITICAL-02d: Index OOB
Fx.java
Math.max/min in substring
CRITICAL-02e: type-safe equality
Fx.java
String.valueOf().equals()
LLM LOCAL base URL
AiProvider.java
localhost:11434 (Ollama)
LLM LOCAL models
AiProviderModels.java
11 practical models
LLM LOCAL timeouts
LocalLlmApiClient.java
connect 15s, read 600s
Morph API key URL
AiSettingsActivity.java
/dashboard/api-keys
Phase 1 — Design Editor + AI BottomSheet
Changes:
Fix
File
Detail
Design bottom bar
design.xml
remove undo/redo, enlarge page name
loadModelsForProvider
ChatActivity.java
fallback to static models
LOCAL_LLM from BottomSheet
ChatActivity.java
exclude LOCAL_LLM from selector
default-enabled consistency
ChatActivity.java
matches AiSettingsActivity
getKeySourceLabel complete
AiProviderAdapter.java
22 providers with URLs
getFreeProviderWebsite
AiProviderAdapter.java
Visit Website for free providers
addProject targeted
ProjectsFragment.java
notifyItemInserted(0)
updateProject targeted
ProjectsFragment.java
notifyItemChanged(index)
backupProject background
ProjectsAdapter.java
separate Thread
Phase 2 — All Operator Blocks + Design Bar + ANR + AirForce
Key changes:
Operator category specs — before vs after:
Before
After
a("b", "<")
a("%d < %d", "b", "<")
a("b", "&&")
a("%b && %b", "b", "&&")
a("b", "not")
a("! %b", "b", "not")
a("d", "+")
a("%d + %d", "d", "+")
a("d", "random")
a("random %d to %d", "d", "random")
a("b", "stringEquals")
a("%s = %s", "b", "stringEquals")
a("s", "trim")
a("trim %s", "s", "trim")
a("s", "toSHA1")
a("SHA1 %s", "s", "toSHA1")
+ 35 more
all with proper spec
deleteProject ANR fix — root cause:
Java
Phase 3 — AirForce Provider + SketchwareAiPipeline
New file: java/pro/sketchware/ai/tools/SketchwareAiPipeline.java
394-line comprehensive pipeline system prompt
10 tool categories each with step-by-step mandatory pipeline
All pipeline rules enforced as Java constants
getCategoryFor(), requiresReadFirst(), requiresConfirmation() helpers
AirForce AI replaces Chutes AI:
Internal enum constant: CHUTES (preserved for compilation)
Display name: "AirForce AI"
Base URL: "https://api.airforce"
Free, no API key, 10 models: GPT-4o, Claude, Gemini, Llama 4, DeepSeek V3...
6. Screen Reports
📱 Main Screen (Project List)
Description: Home screen displaying all user projects in a RecyclerView.
Key files: MainActivity.java, ProjectsFragment.java, ProjectsAdapter.java
Issues & Status:
Issue
Severity
Status
Fix
File I/O on UI Thread
🔴
⚠️ Partial
Full ProjectOperationsManager
notifyDataSetChanged
🔴
✅ Fixed
notifyItemInserted/Changed/Removed
backupProject on UI Thread
🔴
✅ Fixed
Separate Thread
deleteProject ANR on scroll
🔴
✅ Fixed
notifyItemRemoved immediately
Fragment state loss
🟠
❌
safeNavigate()
Dialog memory leaks
🟠
❌
WeakReference + cleanup
No skeleton loading
🟡
❌
ShimmerRecyclerView
No swipe-to-delete
🟡
❌
ItemTouchHelper
Development proposals:
Pagination for many projects (50+)
Sort options (date / name / size / last modified)
Multi-select for bulk delete
Beautiful empty state with CTA
Project tags/categories
Quick actions on swipe (build, backup, duplicate)
Project stats in card (screen count, last build time)
Known memory leaks:
Java
🎨 Design Editor
Description: Visual drag-and-drop editor for building Android UIs.
Key files: DesignActivity.java, res/layout/design.xml
Bottom Bar state (after fix):
Code
Note: btn_undo and btn_redo removed from bar; kept as hidden stubs for backward compat
Issues & Status:
Issue
Severity
Status
Fix
Slow rendering
🟠
❌
Canvas caching for static elements
Rough drag & drop
🟠
❌
velocity + snap threshold
Excessive measure passes
🟠
❌
reduce nested layouts
No full undo/redo
🟡
❌
Command pattern stack
No XML preview
🟡
❌
Split view with XML
Duplicate ID risks
🟠
❌
ID registry when adding views
Development proposals:
XML Inspector panel on the side
Grid/Guideline overlay (toggle on/off)
Constraint distance lines between elements
History panel (change timeline)
Component library (ready-made widgets)
Copy/paste views with hierarchy
Multi-select + alignment tools
Direct Export as XML
🧠 Logic Editor (Block Editor)
Description: The visual block programming editor — Sketchware's true heart.
Key files: LogicEditorActivity.java, BlocksHandler.java, Fx.java
Block state after fixes:
Code
Code Generator fixes (Fx.java):
Block
Original Problem
Fix
/
ArithmeticException
(b != 0 ? (double)a/b : 0.0)
%
ArithmeticException
(b != 0 ? a%b : 0)
=
type mismatch
String.valueOf(a).equals(String.valueOf(b))
stringLength
NullPointerException
(s != null ? s.length() : 0)
stringJoin
NullPointerException
String.valueOf(a) + String.valueOf(b)
stringContains
NullPointerException
guard both sides
stringEquals
NullPointerException
null-safe equals
stringIndex
NullPointerException
guard + returns -1
stringSub
StringIndexOutOfBounds
Math.max/min clamping
Remaining issues:
Issue
Severity
Status
Viewport rendering (all blocks drawn)
🔴
❌ Not fixed
GC pressure (allocation in onDraw)
🟠
❌ Not fixed
No block pooling
🟠
❌
Scroll jank on large projects
🟡
❌
Required viewport fix:
Java
Development proposals:
Block search/filter in palette
Block categories collapse/expand
Mini-map for large canvases
Block color coding by type
Quick block insert with keyboard shortcut
Block templates (ready-made code snippets)
Block graph visualization (flowchart view)
📚 Library Manager
Description: Manages Firebase, AdMob, Maven dependencies, .aar/.jar files.
Current state:
Old "Download a Library" FAB removed ✅
Function moved to toolbar action icon ✅
Remaining issues:
Issue
Severity
No dependency conflict detector
🟠
Gradle injection sometimes unstable
🟠
No version catalog
🟡
No dry-run build validation
🟡
Development proposals:
Dependency conflict visualizer (graph)
"Check compatibility" before adding any library
Favorites library for most-used ones
Auto-update notifications for attached libraries
🤖 AI Settings Screen
Description: Configure and enable AI providers, manage API keys, set up Local LLM.
Key files: AiSettingsActivity.java, activity_ai_settings.xml, AiProviderAdapter.java
Current state:
Code
Completed improvements:
Get Key for all 22 providers opens correct URL ✅
Visit Website for free providers (AirForce, SambaNova, HuggingFace) ✅
LLM LOCAL: clear hint, network guide, defaulted to Ollama port ✅
Morph: URL updated to /dashboard/api-keys ✅
Development proposals:
Provider comparison table (speed, cost, context length)
Test connection for each provider directly from Settings
Provider health status (ping indicator)
API key validation on input
Usage statistics per provider
Import/Export settings
💬 AI Chat Screen
Description: Conversation interface with the AI Agent for creating and editing projects.
Key files: ChatActivity.java, ChatBottomSheetFragment.java
Completed fixes:
Model selector BottomSheet shows provider models even before Refresh ✅
LOCAL_LLM excluded from model selector list ✅
Default-enabled matches AiSettingsActivity ✅
Rate limit hint: "Groq ∞ or Cerebras" instead of wrong "AirForce" ✅
Development proposals:
Auto-failover on rate limit (auto-switch to another provider)
Chat history persistence
Context window indicator (tokens used)
Quick actions: "Build project", "Fix errors", "Explain code"
Code preview before applying (diff view)
Undo for AI-applied changes
🏗️ Build Screen
Build pipeline:
Code
Known build issues:
Issue
Severity
Status
Edge-to-Edge R.id._main
🔴
Blocked (incorrect original description)
Unstable Gradle injection
🟠
❌
No dependency conflict resolver
🟠
❌
Unclear build error messages
🟡
❌
Development proposals:
Build progress step indicator (Compile → Dex → Package → Install)
Incremental build (only changed files)
Build warnings separate from errors
APK size analyzer
Build time tracker
Auto-fix first compile error
7. AI System
Complete AI System Flow
Code
System Prompt — SketchwareAiPipeline.java
Location: java/pro/sketchware/ai/tools/SketchwareAiPipeline.java
10 Core Rules (NEVER BREAK)
Code
Detailed Tool Pipelines
Pipeline 1: UI Layout
Code
Pipeline 2: Block Logic
Code
Pipeline 3: Java Code
Code
Pipeline 4: XML Resources
Code
Pipeline 5: Libraries
Code
Pipeline 6: Build
Code
Pipeline 7: Fix Compile Error
Code
Pipeline 8: Fix Runtime Crash
Code
Tool Classification
Tier 1: Read-Only (safe — call freely)
Code
Tier 2: Write (require prior read)
Code
Tier 3: Destructive (require user confirmation)
Code
Tier 4: Build (long-running)
Code
All Registered Tools (ToolRegistry.java)
Java
ABSOLUTE FORBIDDEN Operations
Code
8. Block System
Block Definition Structure
Java
How a Block Generates Code
Code
All Block Categories in Palette
Code
9. Build System
Build Pipeline
Code
Known Build Issues
Issue
Severity
Status
Edge-to-Edge R.id._main
🔴
Blocked (incorrect original description)
Unstable Gradle injection
🟠
❌
No dependency conflict resolver
🟠
❌
Unclear build error messages
🟡
❌
10. Performance & Memory
Known Memory Leaks
1. Dialog leaks:
Java
2. Static Context references:
Java
3. GC Pressure in Block Rendering:
Java
Recommended RecyclerView Settings
Java
Block Graph Cache
Java
11. Security
Known Vulnerabilities
Vulnerability
Severity
Fix
Exported Activities without permissions
🟠
Add android:exported="false"
File path traversal in import
🟠
Path validation
API keys in unencrypted SharedPreferences
🟠
EncryptedSharedPreferences
Dynamic class loading without signature check
🟠
Signature verification
android:allowBackup=true
🟡
Disable or restrict
12. UI/UX & Material 3
Material 3 Migration Status
Code
Known UI Issues
Issue
Impact
Hardcoded colors (#RRGGBB)
Breaks dark mode
Touch targets < 48dp
Usability problems
Inconsistent corner radius
Unprofessional appearance
RTL not supported everywhere
Arabic users affected
Missing empty states
Poor UX
General UX Proposals
Onboarding for new users
Tooltips for non-obvious buttons
Haptic feedback on drag
Success animations on build completion
Error bottom sheet instead of Toast for long errors
Quick actions (FAB with popup menu)
13. AI Providers
Complete Provider Table
Provider
Free?
API Key Source
Best Model
Notes
Google AI Studio
✅
aistudio.google.com
gemini-2.5-pro
Enabled by default
SambaNova
✅
cloud.sambanova.ai
Llama 4 Maverick
Enabled by default
AirForce AI
✅
No key needed
gpt-4o
Replaces Chutes, default
Groq
Partial free
console.groq.com
llama-3.3-70b
Effectively unlimited
Cerebras
Partial free
cloud.cerebras.ai
llama3.1-8b
Fastest inference
OpenAI
Paid
platform.openai.com
gpt-4o
Most stable
Anthropic
Paid
console.anthropic.com
claude-3-5-sonnet
Prompt caching
DeepSeek
Cheap
platform.deepseek.com
deepseek-chat
Cost-effective
Gemini
Paid
aistudio.google.com
gemini-2.0-flash

Morph LLM
—
morphllm.com/dashboard/api-keys
—
Layout only
Local LLM
Free
No key needed
gemma3:12b
Ollama on network
+ 15 more providers
—
—
—

Critical Provider Notes
AirForce AI (enabled by default):
Code
Local LLM (Ollama):
Code
14. Roadmap
Current State: Phase 0-5 Complete ✅
Next Phase: Phase 6 — AI Integration Across All Screens
Goal: Enable AI Agent in:
Design Editor (direct layout modification)
Logic Editor (add/modify blocks)
Build screen (auto-fix errors)
Library Manager (suggest libraries)
Prerequisites before starting:
Code
Complete Development Roadmap
Code
15. Development Rules
Immutable Rules — Never Break
Code
AI Pipeline Rules
Code
How to Add a New AI Provider
Java
How to Read the Block System (for new contributors)
Code
This file is the single source of truth for the project. Last update: Phase 5
To contribute: follow development rules, read before modifying, always test the build.

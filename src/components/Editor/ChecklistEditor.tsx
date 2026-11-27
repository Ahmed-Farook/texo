import React, { useState, useEffect, useLayoutEffect, useRef, useCallback } from 'react';
import { Note } from '../../types/note';
import { triggerHaptic } from '../../utils/haptics';
import { syncNoteToAndroidWidget } from '../../utils/widgetSync';
import { useNotes } from '../../store/NotesContext';
import { hasRichNoteContent } from '../../utils/storage';
import { Check, ChevronDown, X } from 'lucide-react';

interface ChecklistItem {
  id: string;
  text: string;
}

/**
 * Multi-line auto-resizing textarea so long tasks and titles wrap cleanly
 * instead of scrolling horizontally.
 */
const AutoResizeTextarea = React.forwardRef<
  HTMLTextAreaElement,
  React.TextareaHTMLAttributes<HTMLTextAreaElement>
>(({ value, className = '', ...props }, forwardedRef) => {
  const innerRef = useRef<HTMLTextAreaElement | null>(null);

  const setRefs = useCallback(
    (node: HTMLTextAreaElement | null) => {
      innerRef.current = node;
      if (typeof forwardedRef === 'function') {
        forwardedRef(node);
      } else if (forwardedRef) {
        (forwardedRef as React.MutableRefObject<HTMLTextAreaElement | null>).current = node;
      }
    },
    [forwardedRef]
  );

  const resize = useCallback(() => {
    const el = innerRef.current;
    if (!el) return;
    el.style.height = 'auto';
    el.style.height = `${el.scrollHeight}px`;
  }, []);

  useLayoutEffect(() => {
    resize();
  }, [value, resize]);

  useEffect(() => {
    const el = innerRef.current;
    if (!el || typeof ResizeObserver === 'undefined') return;
    const ro = new ResizeObserver(() => resize());
    ro.observe(el);
    return () => ro.disconnect();
  }, [resize]);

  return (
    <textarea
      ref={setRefs}
      rows={1}
      value={value}
      className={className}
      {...props}
    />
  );
});
AutoResizeTextarea.displayName = 'AutoResizeTextarea';

interface ChecklistEditorProps {
  note: Note;
  onUpdate: (updates: Partial<Note>) => void;
  isReadOnly?: boolean;
  theme?: 'light' | 'dark';
}

function escapeHtml(str: string): string {
  return str
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;');
}

function parseChecklistHtml(html: string, fallbackTitle: string): {
  title: string;
  activeTasks: ChecklistItem[];
  completedTasks: ChecklistItem[];
} {
  let title = fallbackTitle || 'To-Do List';
  const activeTasks: ChecklistItem[] = [];
  const completedTasks: ChecklistItem[] = [];

  if (!html) {
    return { title, activeTasks, completedTasks };
  }

  try {
    if (typeof document !== 'undefined') {
      const doc = new DOMParser().parseFromString(html, 'text/html');
      const h1 = doc.querySelector('h1');
      if (h1 && h1.textContent?.trim()) {
        title = h1.textContent.trim();
      }

      const taskNodes = doc.querySelectorAll('li[data-type="taskItem"]');
      taskNodes.forEach((node, idx) => {
        const isChecked =
          node.getAttribute('data-checked') === 'true' ||
          Boolean(node.querySelector('input[type="checkbox"]:checked'));

        const clone = node.cloneNode(true) as HTMLElement;
        clone.querySelectorAll('input, label').forEach((el) => el.remove());
        const text = clone.textContent?.trim() || '';

        if (text) {
          const item: ChecklistItem = {
            id: `task-${idx}-${Math.random().toString(36).slice(2, 6)}`,
            text,
          };
          if (isChecked) {
            completedTasks.push(item);
          } else {
            activeTasks.push(item);
          }
        }
      });

      return { title, activeTasks, completedTasks };
    }
  } catch (_) {}

  // Fallback regex parsing
  const titleMatch = html.match(/<h1>([\s\S]*?)<\/h1>/i);
  if (titleMatch && titleMatch[1]) {
    title = titleMatch[1].replace(/<[^>]*>/g, '').trim();
  }

  const taskRegex =
    /<li[^>]*data-type=["']taskItem["'][^>]*data-checked=["'](true|false)["'][^>]*>([\s\S]*?)<\/li>/gi;
  let match: RegExpExecArray | null;
  let idx = 0;
  while ((match = taskRegex.exec(html)) !== null) {
    const isChecked = match[1].toLowerCase() === 'true';
    const text = match[2].replace(/<[^>]*>/g, '').trim();
    if (text) {
      const item: ChecklistItem = {
        id: `task-${idx++}-${Math.random().toString(36).slice(2, 6)}`,
        text,
      };
      if (isChecked) {
        completedTasks.push(item);
      } else {
        activeTasks.push(item);
      }
    }
  }

  return { title, activeTasks, completedTasks };
}

function serializeChecklistHtml(
  title: string,
  active: ChecklistItem[],
  completed: ChecklistItem[]
): string {
  const safeTitle = escapeHtml(title.trim() || 'To-Do List');
  const activeLis = active
    .filter((t) => t.text.trim().length > 0)
    .map(
      (t) =>
        `<li data-type="taskItem" data-checked="false"><label><input type="checkbox"><span></span></label><div><p>${escapeHtml(
          t.text
        )}</p></div></li>`
    )
    .join('');

  const completedLis = completed
    .filter((t) => t.text.trim().length > 0)
    .map(
      (t) =>
        `<li data-type="taskItem" data-checked="true"><label><input type="checkbox" checked="checked"><span></span></label><div><p>${escapeHtml(
          t.text
        )}</p></div></li>`
    )
    .join('');

  const lis =
    activeLis + completedLis ||
    '<li data-type="taskItem" data-checked="false"><label><input type="checkbox"><span></span></label><div><p></p></div></li>';

  return `<h1>${safeTitle}</h1><ul data-type="taskList">${lis}</ul>`;
}

export const ChecklistEditor: React.FC<ChecklistEditorProps> = ({
  note,
  onUpdate,
  isReadOnly = false,
}) => {
  const { notes } = useNotes();
  const [title, setTitle] = useState(note.title || 'To-Do List');
  const [activeTasks, setActiveTasks] = useState<ChecklistItem[]>([]);
  const [completedTasks, setCompletedTasks] = useState<ChecklistItem[]>([]);
  const [adderText, setAdderText] = useState('');
  const [isCompletedCollapsed, setIsCompletedCollapsed] = useState(false);

  const adderInputRef = useRef<HTMLTextAreaElement>(null);
  const taskInputRefs = useRef<Map<string, HTMLTextAreaElement>>(new Map());
  const lastSavedHtmlRef = useRef<string>('');
  const currentNoteIdRef = useRef<string>(note.id);

  // Sync internal state when active note changes or external updates arrive
  useEffect(() => {
    const isNewNote = currentNoteIdRef.current !== note.id;
    currentNoteIdRef.current = note.id;

    if (isNewNote || note.content !== lastSavedHtmlRef.current) {
      const parsed = parseChecklistHtml(note.content, note.title);
      setTitle(parsed.title);
      setActiveTasks(parsed.activeTasks);
      setCompletedTasks(parsed.completedTasks);
      lastSavedHtmlRef.current = note.content;

      // Focus adder input for newly opened / created empty checklist
      if (isNewNote && !isReadOnly && parsed.activeTasks.length === 0) {
        setTimeout(() => {
          adderInputRef.current?.focus();
        }, 80);
      }
    }
  }, [note.id, note.content, note.title, isReadOnly]);

  // Persist and synchronize to storage and Android home screen widget
  const commitChanges = useCallback(
    (newTitle: string, newActive: ChecklistItem[], newCompleted: ChecklistItem[]) => {
      // SAFETY GUARD: If activeTasks and completedTasks are both empty, but note.content originally had text,
      // NEVER overwrite the note with an empty checklist!
      if (newActive.length === 0 && newCompleted.length === 0 && (hasRichNoteContent(note.content) || (note.content && note.content.length > 50))) {
        return;
      }

      const serialized = serializeChecklistHtml(newTitle, newActive, newCompleted);
      lastSavedHtmlRef.current = serialized;

      const total = newActive.length + newCompleted.length;
      const plainTextPreview =
        total > 0
          ? `${newCompleted.length}/${total} completed`
          : '0 tasks completed';

      const cleanTitle = newTitle.trim() || 'To-Do List';

      onUpdate({
        title: cleanTitle,
        content: serialized,
        plainTextPreview,
      });

      // Synchronize with Android home screen widget
      if (notes && notes.length > 0) {
        const updatedNotes = notes.map((n) =>
          n.id === note.id
            ? { ...n, title: cleanTitle, content: serialized, plainTextPreview }
            : n
        );
        syncNoteToAndroidWidget(updatedNotes, note.id);
      }
    },
    [note.id, note.content, notes, onUpdate]
  );

  // Title change handler
  const handleTitleChange = (val: string) => {
    setTitle(val);
    commitChanges(val, activeTasks, completedTasks);
  };

  // First line adder action: creates task and adds to active tasks
  const handleAddNewTask = (overrideText?: string) => {
    const raw = overrideText !== undefined ? overrideText : adderText;
    const text = raw.replace(/[\r\n]+/g, ' ').trim();
    if (!text) return;

    const newTask: ChecklistItem = {
      id: `task-${Date.now()}-${Math.random().toString(36).slice(2, 6)}`,
      text,
    };

    const nextActive = [...activeTasks, newTask];
    setActiveTasks(nextActive);
    setAdderText('');
    triggerHaptic();
    commitChanges(title, nextActive, completedTasks);

    // Keep focus in adder input ready for quick consecutive entries
    adderInputRef.current?.focus();
  };

  // First line adder key down handler
  const handleAdderKeyDown = (e: React.KeyboardEvent<HTMLTextAreaElement>) => {
    if ((e.key === 'Enter' && !e.shiftKey) || e.keyCode === 13 || e.which === 13) {
      e.preventDefault();
      handleAddNewTask();
    }
  };

  // Active task text change (also handles Android IME soft-keyboard Enter inserting '\n')
  const handleTaskTextChange = (index: number, id: string, rawText: string) => {
    if (rawText.includes('\n')) {
      const nlIdx = rawText.indexOf('\n');
      const before = rawText.slice(0, nlIdx).replace(/[\r\n]+/g, '');
      const after = rawText.slice(nlIdx + 1).replace(/[\r\n]+/g, '');
      const newTask: ChecklistItem = {
        id: `task-${Date.now()}-${Math.random().toString(36).slice(2, 6)}`,
        text: after,
      };
      const nextActive = [
        ...activeTasks.slice(0, index),
        { ...activeTasks[index], text: before },
        newTask,
        ...activeTasks.slice(index + 1),
      ];
      setActiveTasks(nextActive);
      triggerHaptic();
      commitChanges(title, nextActive, completedTasks);
      setTimeout(() => {
        const nextEl = taskInputRefs.current.get(newTask.id);
        if (nextEl) {
          nextEl.focus();
          nextEl.setSelectionRange(0, 0);
        }
      }, 40);
      return;
    }

    const nextActive = activeTasks.map((t) => (t.id === id ? { ...t, text: rawText } : t));
    setActiveTasks(nextActive);
    commitChanges(title, nextActive, completedTasks);
  };

  // Active task keyboard navigation / creation / deletion
  const handleActiveTaskKeyDown = (
    e: React.KeyboardEvent<HTMLTextAreaElement>,
    index: number,
    taskId: string
  ) => {
    if (e.key === 'Backspace' && activeTasks[index]?.text === '') {
      e.preventDefault();
      handleDeleteActiveTask(taskId);
      if (index > 0) {
        const prevTask = activeTasks[index - 1];
        taskInputRefs.current.get(prevTask.id)?.focus();
      } else {
        adderInputRef.current?.focus();
      }
    } else if ((e.key === 'Enter' && !e.shiftKey) || e.keyCode === 13 || e.which === 13) {
      e.preventDefault();
      const curText = activeTasks[index]?.text || '';
      const selStart = e.currentTarget.selectionStart ?? curText.length;
      const selEnd = e.currentTarget.selectionEnd ?? curText.length;
      const beforeCursor = curText.slice(0, selStart);
      const afterCursor = curText.slice(selEnd);

      const newTask: ChecklistItem = {
        id: `task-${Date.now()}-${Math.random().toString(36).slice(2, 6)}`,
        text: afterCursor,
      };
      const nextActive = [
        ...activeTasks.slice(0, index),
        { ...activeTasks[index], text: beforeCursor },
        newTask,
        ...activeTasks.slice(index + 1),
      ];
      setActiveTasks(nextActive);
      triggerHaptic();
      commitChanges(title, nextActive, completedTasks);
      setTimeout(() => {
        const nextEl = taskInputRefs.current.get(newTask.id);
        if (nextEl) {
          nextEl.focus();
          nextEl.setSelectionRange(0, 0);
        }
      }, 40);
    }
  };

  // Complete an active task: moves to Completed section
  const handleCompleteTask = (taskId: string) => {
    const task = activeTasks.find((t) => t.id === taskId);
    if (!task) return;
    triggerHaptic();

    const nextActive = activeTasks.filter((t) => t.id !== taskId);
    const nextCompleted = [task, ...completedTasks];

    setActiveTasks(nextActive);
    setCompletedTasks(nextCompleted);
    commitChanges(title, nextActive, nextCompleted);
  };

  // Untick a completed task: moves back to Active tasks
  const handleUntickTask = (taskId: string) => {
    const task = completedTasks.find((t) => t.id === taskId);
    if (!task) return;
    triggerHaptic();

    const nextCompleted = completedTasks.filter((t) => t.id !== taskId);
    const nextActive = [...activeTasks, task];

    setCompletedTasks(nextCompleted);
    setActiveTasks(nextActive);
    commitChanges(title, nextActive, nextCompleted);
  };

  // Delete active task
  const handleDeleteActiveTask = (taskId: string) => {
    const nextActive = activeTasks.filter((t) => t.id !== taskId);
    setActiveTasks(nextActive);
    commitChanges(title, nextActive, completedTasks);
  };

  // Delete completed task
  const handleDeleteCompletedTask = (taskId: string) => {
    const nextCompleted = completedTasks.filter((t) => t.id !== taskId);
    setCompletedTasks(nextCompleted);
    commitChanges(title, activeTasks, nextCompleted);
  };

  // Clear all completed tasks
  const handleClearAllCompleted = () => {
    triggerHaptic();
    setCompletedTasks([]);
    commitChanges(title, activeTasks, []);
  };

  return (
    <div
      className="tiptap prose prose-neutral dark:prose-invert max-w-none focus:outline-none min-h-[140px] pb-32 select-text"
      data-pdf-note-id={note.id}
    >
      {/* Checklist Title Heading with generous 1-line breathing room (2.75rem / 44px) matching standard notes */}
      <h1 className="mb-11" style={{ marginBottom: '2.75rem' }}>
        <AutoResizeTextarea
          value={title}
          disabled={isReadOnly}
          enterKeyHint="next"
          onChange={(e) => {
            const val = e.target.value;
            if (val.includes('\n')) {
              handleTitleChange(val.replace(/[\r\n]+/g, ''));
              adderInputRef.current?.focus();
              return;
            }
            handleTitleChange(val);
          }}
          onKeyDown={(e) => {
            if ((e.key === 'Enter' && !e.shiftKey) || e.keyCode === 13 || e.which === 13) {
              e.preventDefault();
              adderInputRef.current?.focus();
            }
          }}
          placeholder="To-Do List"
          className="w-full p-0 m-0 bg-transparent border-none outline-none resize-none overflow-hidden whitespace-pre-wrap break-words font-bold text-[2rem] sm:text-[2.25rem] text-[#1C2B36] dark:text-[#E8EEF3] tracking-tight placeholder:text-[#8AA0B4]/40 leading-[2.5rem]"
          aria-label="Checklist Title"
        />
      </h1>

      {/* Line 1: Quick List Adder (Type task here and press Enter to add) */}
      {!isReadOnly && (
        <div className="flex items-start gap-3 py-1.5 px-0.5 group">
          <button
            type="button"
            onClick={() => handleAddNewTask()}
            disabled={!adderText.trim()}
            className="w-[1.15rem] h-[1.15rem] mt-[3px] rounded-full border-[1.5px] border-[#c2b5a1] dark:border-[#3b5062] hover:border-[#213A50] dark:hover:border-[#4B708B] flex-shrink-0 grid place-content-center opacity-60 hover:opacity-100 transition-all cursor-pointer"
            aria-label="Add task"
            title="Add task"
          />
          <AutoResizeTextarea
            ref={adderInputRef}
            enterKeyHint="done"
            value={adderText}
            onChange={(e) => {
              const val = e.target.value;
              if (val.includes('\n')) {
                const clean = val.replace(/[\r\n]+/g, '').trim();
                if (clean) {
                  handleAddNewTask(clean);
                } else {
                  setAdderText('');
                }
                return;
              }
              setAdderText(val);
            }}
            onKeyDown={handleAdderKeyDown}
            placeholder="Type a task and press Enter..."
            className="flex-1 min-w-0 p-0 m-0 bg-transparent border-none outline-none resize-none overflow-hidden whitespace-pre-wrap break-words text-[15px] sm:text-base text-[#213A50] dark:text-[#E8EEF3] placeholder:text-[#8AA0B4]/60 dark:placeholder:text-[#52667A]/60 leading-relaxed"
            aria-label="Add task"
          />
        </div>
      )}

      {/* Active Tasks List */}
      <div className="space-y-1 mt-1">
        {activeTasks.map((task, index) => (
          <div
            key={task.id}
            className="group flex items-start gap-3 py-1 px-0.5 rounded-lg transition-colors"
          >
            {/* Interactive Checkbox */}
            <button
              type="button"
              disabled={isReadOnly}
              onClick={() => handleCompleteTask(task.id)}
              className="w-[1.15rem] h-[1.15rem] mt-[3px] rounded-full border-[1.5px] border-[#c2b5a1] dark:border-[#3b5062] hover:border-[#213A50] dark:hover:border-[#4B708B] flex-shrink-0 transition-all flex items-center justify-center cursor-pointer"
              aria-label="Mark task as completed"
            />

            {/* Editable Multi-line Wrapping Task Text */}
            <AutoResizeTextarea
              ref={(el) => {
                if (el) taskInputRefs.current.set(task.id, el);
                else taskInputRefs.current.delete(task.id);
              }}
              enterKeyHint="enter"
              disabled={isReadOnly}
              value={task.text}
              onChange={(e) => handleTaskTextChange(index, task.id, e.target.value)}
              onKeyDown={(e) => handleActiveTaskKeyDown(e, index, task.id)}
              className="flex-1 min-w-0 p-0 m-0 bg-transparent border-none outline-none resize-none overflow-hidden whitespace-pre-wrap break-words text-[15px] sm:text-base text-[#213A50] dark:text-[#E8EEF3] leading-relaxed"
            />

            {/* Delete button (visible on mobile touch, hover on desktop) */}
            {!isReadOnly && (
              <button
                type="button"
                onClick={() => handleDeleteActiveTask(task.id)}
                className="opacity-60 md:opacity-0 md:group-hover:opacity-100 transition-opacity p-1 -mr-1 text-[#8AA0B4] hover:text-red-500 active:text-red-500 rounded flex-shrink-0 cursor-pointer touch-manipulation"
                aria-label="Delete task"
                title="Delete task"
              >
                <X size={16} />
              </button>
            )}
          </div>
        ))}
      </div>

      {/* Dedicated Completed Section at Bottom */}
      {completedTasks.length > 0 && (
        <div className="mt-8 pt-4 border-t border-[#DFD7CC]/60 dark:border-[#223344]/80">
          <div className="flex items-center justify-between mb-3 select-none">
            <button
              type="button"
              onClick={() => setIsCompletedCollapsed((prev) => !prev)}
              className="flex items-center gap-1.5 text-xs font-semibold uppercase tracking-wider text-[#6E8092] dark:text-[#8AA0B4] hover:text-[#213A50] dark:hover:text-[#E8EEF3] transition-colors cursor-pointer"
              aria-expanded={!isCompletedCollapsed}
            >
              <ChevronDown
                size={14}
                className={`transition-transform duration-200 ${
                  isCompletedCollapsed ? '-rotate-90' : ''
                }`}
              />
              <span>Completed ({completedTasks.length})</span>
            </button>

            {!isReadOnly && (
              <button
                type="button"
                onClick={handleClearAllCompleted}
                className="text-[11px] font-medium text-[#8AA0B4] hover:text-red-500 transition-colors cursor-pointer"
              >
                Clear all
              </button>
            )}
          </div>

          {!isCompletedCollapsed && (
            <div className="space-y-1">
              {completedTasks.map((task) => (
                <div
                  key={task.id}
                  className="group flex items-start gap-3 py-1 px-0.5 rounded-lg transition-colors"
                >
                  {/* Checked Checkbox: clicking unticks and restores task to active list */}
                  <button
                    type="button"
                    disabled={isReadOnly}
                    onClick={() => handleUntickTask(task.id)}
                    className="w-[1.15rem] h-[1.15rem] mt-0.5 rounded-full bg-[#213A50] dark:bg-[#4B708B] border-[1.5px] border-[#213A50] dark:border-[#4B708B] flex-shrink-0 grid place-content-center text-white transition-transform active:scale-90 cursor-pointer"
                    aria-label="Untick task"
                    title="Untick task"
                  >
                    <Check size={11} strokeWidth={3} />
                  </button>

                  {/* Non-editable Strikethrough Text: cannot edit text, only untickable */}
                  <span className="flex-1 min-w-0 break-words text-[15px] sm:text-base line-through text-[#8a9ba8] dark:text-[#627788] select-text cursor-default leading-relaxed">
                    {task.text}
                  </span>

                  {/* Delete completed task button (visible on mobile touch, hover on desktop) */}
                  {!isReadOnly && (
                    <button
                      type="button"
                      onClick={() => handleDeleteCompletedTask(task.id)}
                      className="opacity-60 md:opacity-0 md:group-hover:opacity-100 transition-opacity p-1 -mr-1 text-[#8AA0B4] hover:text-red-500 active:text-red-500 rounded flex-shrink-0 cursor-pointer touch-manipulation"
                      aria-label="Delete task"
                      title="Delete task"
                    >
                      <X size={16} />
                    </button>
                  )}
                </div>
              ))}
            </div>
          )}
        </div>
      )}

      {/* Generous buffer area: clicking focuses the adder input */}
      <div
        className="h-28 w-full cursor-text select-none"
        onClick={() => {
          if (!isReadOnly) {
            adderInputRef.current?.focus();
          }
        }}
        aria-hidden="true"
      />
    </div>
  );
};

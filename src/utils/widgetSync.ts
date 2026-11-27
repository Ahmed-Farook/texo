import { Capacitor } from '@capacitor/core';
import { Note } from '../types/note';
import { isChecklistNote } from './storage';

export interface WidgetTodoItem {
  text: string;
  checked: boolean;
}

export const WIDGET_PINNED_NOTE_KEY = 'texo_pinned_widget_note_id';

export function getWidgetPinnedNoteId(): string | null {
  if (typeof window === 'undefined') return null;
  return localStorage.getItem(WIDGET_PINNED_NOTE_KEY);
}

export function setWidgetPinnedNoteId(noteId: string | null): void {
  if (typeof window === 'undefined') return;
  if (noteId) {
    localStorage.setItem(WIDGET_PINNED_NOTE_KEY, noteId);
  } else {
    localStorage.removeItem(WIDGET_PINNED_NOTE_KEY);
  }
}

/**
 * Extracts task list checklist items from a Note's HTML content.
 */
export function extractChecklistFromHtml(html: string): WidgetTodoItem[] {
  if (!html) return [];
  const items: WidgetTodoItem[] = [];

  try {
    if (typeof document !== 'undefined') {
      const doc = new DOMParser().parseFromString(html, 'text/html');
      const taskNodes = doc.querySelectorAll('li[data-type="taskItem"], li:has(input[type="checkbox"])');

      taskNodes.forEach((node) => {
        const checkbox = node.querySelector('input[type="checkbox"]') as HTMLInputElement | null;
        const isChecked = node.getAttribute('data-checked') === 'true' || Boolean(checkbox?.checked);

        // Clone node and remove checkbox element to get pure text content
        const clone = node.cloneNode(true) as HTMLElement;
        const cb = clone.querySelector('input[type="checkbox"]');
        if (cb) cb.remove();

        const text = (clone.textContent || '').trim();
        if (text) {
          items.push({
            text,
            checked: isChecked,
          });
        }
      });
    }
  } catch (_) {}

  // Fallback regex parser if DOMParser is unavailable or finds none
  if (items.length === 0) {
    const taskRegex = /<li[^>]*data-type=["']taskItem["'][^>]*data-checked=["'](true|false)["'][^>]*>([\s\S]*?)<\/li>/gi;
    let match: RegExpExecArray | null;
    while ((match = taskRegex.exec(html)) !== null) {
      const isChecked = match[1].toLowerCase() === 'true';
      const cleanText = match[2].replace(/<[^>]*>/g, '').trim();
      if (cleanText) {
        items.push({ text: cleanText, checked: isChecked });
      }
    }
  }

  return items;
}

/**
 * Updates theme preference in Android SharedPreferences & updates all home screen widgets + status/nav bars.
 */
export async function syncThemeToAndroid(theme: 'light' | 'dark'): Promise<void> {
  if (!Capacitor.isNativePlatform()) return;
  const win = typeof window !== 'undefined' ? (window as any) : undefined;
  const widgetPlugin = win?.Capacitor?.Plugins?.NotesWidget;
  if (!widgetPlugin?.setWidgetTheme) return;
  try {
    await widgetPlugin.setWidgetTheme({ theme });
  } catch (err) {
    console.warn('Failed to sync theme to Android widgets:', err);
  }
}

function escapeHtmlText(str: string): string {
  return str
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;');
}

function getOrCreateTaskList(doc: Document): HTMLElement {
  let list = doc.querySelector('ul[data-type="taskList"]') as HTMLElement | null;
  if (!list) {
    list = doc.createElement('ul');
    list.setAttribute('data-type', 'taskList');
    doc.body.appendChild(list);
  }
  return list;
}

/**
 * Computes checklist summary string ("2/5 completed" or "0 tasks completed").
 */
function computeChecklistPreview(html: string): string {
  const items = extractChecklistFromHtml(html);
  if (items.length === 0) return '0 tasks completed';
  const done = items.filter((i) => i.checked).length;
  return `${done}/${items.length} completed`;
}

/**
 * Updates taskItem checked state at a specific index in a Note's HTML.
 * Ordering matches ChecklistEditor & Google Tasks:
 * - Checked items move to top of Completed section (first checked item)
 * - Unchecked items move to bottom of Active section (right before first checked item)
 */
export function toggleTaskItemInHtml(html: string, targetIndex: number, checked: boolean): string {
  if (!html) return html;
  try {
    if (typeof document !== 'undefined') {
      const doc = new DOMParser().parseFromString(html, 'text/html');
      // Only consider real non-empty task items (matching extractChecklistFromHtml indices)
      const allTaskNodes = Array.from(doc.querySelectorAll('li[data-type="taskItem"]'));
      const nonEmptyNodes = allTaskNodes.filter((node) => {
        const clone = node.cloneNode(true) as HTMLElement;
        clone.querySelectorAll('input, label').forEach((el) => el.remove());
        return (clone.textContent || '').trim().length > 0;
      });

      const target = nonEmptyNodes[targetIndex];
      if (target) {
        target.setAttribute('data-checked', checked ? 'true' : 'false');
        const cb = target.querySelector('input[type="checkbox"]') as HTMLInputElement | null;
        if (cb) {
          cb.checked = checked;
          if (checked) {
            cb.setAttribute('checked', 'checked');
          } else {
            cb.removeAttribute('checked');
          }
        }

        const parentList = target.parentElement;
        if (parentList && parentList.getAttribute('data-type') === 'taskList') {
          const otherCompleted = Array.from(
            parentList.querySelectorAll('li[data-type="taskItem"][data-checked="true"]')
          ).find((el) => el !== target);

          if (checked) {
            if (otherCompleted) {
              parentList.insertBefore(target, otherCompleted);
            } else {
              parentList.appendChild(target);
            }
          } else {
            if (otherCompleted) {
              parentList.insertBefore(target, otherCompleted);
            } else {
              parentList.appendChild(target);
            }
          }
        }

        return doc.body.innerHTML;
      }
    }
  } catch (_) {}

  return html;
}

/**
 * Deletes taskItem at targetIndex from a Note's HTML.
 */
export function deleteTaskItemInHtml(html: string, targetIndex: number): string {
  if (!html) return html;
  try {
    if (typeof document !== 'undefined') {
      const doc = new DOMParser().parseFromString(html, 'text/html');
      const allTaskNodes = Array.from(doc.querySelectorAll('li[data-type="taskItem"]'));
      const nonEmptyNodes = allTaskNodes.filter((node) => {
        const clone = node.cloneNode(true) as HTMLElement;
        clone.querySelectorAll('input, label').forEach((el) => el.remove());
        return (clone.textContent || '').trim().length > 0;
      });

      const target = nonEmptyNodes[targetIndex];
      if (target) {
        target.remove();
        return doc.body.innerHTML;
      }
    }
  } catch (_) {}
  return html;
}

/**
 * Inserts a new unchecked taskItem at the end of Active items (before first completed item).
 */
export function addTaskItemToHtml(html: string, text: string): string {
  const cleanText = text.trim();
  if (!cleanText) return html;
  try {
    if (typeof document !== 'undefined') {
      const doc = new DOMParser().parseFromString(html || '<h1>To-Do List</h1><ul data-type="taskList"></ul>', 'text/html');
      const list = getOrCreateTaskList(doc);

      // Remove empty placeholder taskItem if list only had placeholder
      Array.from(list.querySelectorAll('li[data-type="taskItem"]')).forEach((node) => {
        const clone = node.cloneNode(true) as HTMLElement;
        clone.querySelectorAll('input, label').forEach((el) => el.remove());
        if ((clone.textContent || '').trim().length === 0) {
          node.remove();
        }
      });

      const li = doc.createElement('li');
      li.setAttribute('data-type', 'taskItem');
      li.setAttribute('data-checked', 'false');
      li.innerHTML = `<label><input type="checkbox"><span></span></label><div><p>${escapeHtmlText(cleanText)}</p></div>`;

      const firstCompleted = list.querySelector('li[data-type="taskItem"][data-checked="true"]');
      if (firstCompleted) {
        list.insertBefore(li, firstCompleted);
      } else {
        list.appendChild(li);
      }

      return doc.body.innerHTML;
    }
  } catch (_) {}

  const taskHtml = `<li data-type="taskItem" data-checked="false"><label><input type="checkbox"><span></span></label><div><p>${escapeHtmlText(cleanText)}</p></div></li>`;
  if (html && html.includes('</ul>')) {
    return html.replace('</ul>', `${taskHtml}</ul>`);
  }
  return `${html || '<h1>To-Do List</h1>'}<ul data-type="taskList">${taskHtml}</ul>`;
}

/**
 * Finds the most relevant note for the widget:
 * 1. The note explicitly pinned to the widget by the user.
 * 2. The currently active note if it has checklist items or text.
 * 3. Or a pinned note that contains checklist items.
 * 4. Or the latest updated note with checklist items.
 * 5. Or the latest non-trash note.
 */
export function findBestWidgetNote(notes: Note[], activeNoteId: string | null): Note | null {
  const activeNotes = notes.filter((n) => !n.isTrash && !n.isLocked);
  if (activeNotes.length === 0) return null;

  // 1. Explicit user selection for widget
  const pinnedWidgetId = getWidgetPinnedNoteId();
  if (pinnedWidgetId) {
    const chosen = activeNotes.find((n) => n.id === pinnedWidgetId);
    if (chosen) return chosen;
  }

  // 2. Active note if it is a checklist
  if (activeNoteId) {
    const active = activeNotes.find((n) => n.id === activeNoteId);
    if (active && isChecklistNote(active)) {
      return active;
    }
  }

  // 3. Most recently updated checklist
  const checklistFolderNote = activeNotes.find((n) => isChecklistNote(n));
  if (checklistFolderNote) return checklistFolderNote;

  // 4. Active note (fallback)
  if (activeNoteId) {
    const active = activeNotes.find((n) => n.id === activeNoteId);
    if (active) return active;
  }

  // 6. Pinned note or first note
  const pinned = activeNotes.find((n) => n.isPinned);
  if (pinned) return pinned;

  return activeNotes[0] || null;
}

/**
 * Syncs the selected note and its checklist items to the Android home screen widget.
 */
export async function syncNoteToAndroidWidget(notes: Note[], activeNoteId: string | null): Promise<void> {
  if (!Capacitor.isNativePlatform()) return;

  const win = typeof window !== 'undefined' ? (window as any) : undefined;
  const widgetPlugin = win?.Capacitor?.Plugins?.NotesWidget;
  if (!widgetPlugin?.updateWidgetData) return;

  const targetNote = findBestWidgetNote(notes, activeNoteId);
  if (!targetNote) {
    try {
      await widgetPlugin.updateWidgetData({
        noteId: '',
        title: 'To-Do List',
        items: [],
      });
    } catch (_) {}
    return;
  }

  const items = extractChecklistFromHtml(targetNote.content || '');

  // If note has no checklist items, include first few plain text lines as items
  const widgetItems =
    items.length > 0
      ? items
      : (targetNote.plainTextPreview || '')
          .split('\n')
          .map((l) => l.trim())
          .filter(Boolean)
          .slice(0, 6)
          .map((text) => ({ text, checked: false }));

  try {
    await widgetPlugin.updateWidgetData({
      noteId: targetNote.id,
      title: targetNote.title || 'To-Do List',
      items: widgetItems,
    });
    // Also sync all available checklists for multi-widget note selection
    await syncAllChecklistsToAndroidWidget(notes);
  } catch (err) {
    console.warn('Failed to sync data to Android widget:', err);
  }
}

/**
 * Syncs all available checklists to Android so each home screen widget can choose and display its list.
 */
export async function syncAllChecklistsToAndroidWidget(notes: Note[]): Promise<void> {
  if (!Capacitor.isNativePlatform()) return;

  const win = typeof window !== 'undefined' ? (window as any) : undefined;
  const widgetPlugin = win?.Capacitor?.Plugins?.NotesWidget;
  if (!widgetPlugin?.syncAllChecklists) return;

  const checklistNotes = notes.filter(
    (n) => !n.isTrash && !n.isLocked && isChecklistNote(n)
  );

  const payload = checklistNotes.map((note) => ({
    id: note.id,
    title: note.title || 'To-Do List',
    items: extractChecklistFromHtml(note.content || ''),
  }));

  try {
    await widgetPlugin.syncAllChecklists({ checklists: payload });
  } catch (err) {
    console.warn('Failed to sync all checklists to Android:', err);
  }
}

/**
 * Checks for any to-do items toggled, added, or deleted directly on the Android widget while the app was in the background.
 */
export async function syncPendingWidgetToggles(
  notes: Note[],
  updateNote: (id: string, updates: Partial<Note>) => void
): Promise<void> {
  if (!Capacitor.isNativePlatform()) return;

  const win = typeof window !== 'undefined' ? (window as any) : undefined;
  const widgetPlugin = win?.Capacitor?.Plugins?.NotesWidget;
  if (!widgetPlugin) return;

  const modifiedHtmlMap = new Map<string, string>();
  const resolveWorkingContent = (noteId: string): { id: string; content: string } | null => {
    const target = notes.find((n) => n.id === noteId) || (noteId ? null : notes.find((n) => isChecklistNote(n)));
    if (!target) return null;
    const currentHtml = modifiedHtmlMap.get(target.id) ?? target.content ?? '';
    return { id: target.id, content: currentHtml };
  };

  try {
    // 1. Unified chronological widget operations queue (toggle / delete / add / clear_completed)
    if (widgetPlugin.getPendingWidgetOps) {
      const opsRes = await widgetPlugin.getPendingWidgetOps();
      const ops = opsRes?.ops;
      if (Array.isArray(ops) && ops.length > 0) {
        for (const item of ops) {
          const { op, noteId, itemIndex, checked, text } = item;
          const resolved = resolveWorkingContent(noteId || '');
          if (!resolved) continue;

          let nextHtml = resolved.content;
          if (op === 'toggle' && typeof itemIndex === 'number') {
            nextHtml = toggleTaskItemInHtml(resolved.content, itemIndex, Boolean(checked));
          } else if (op === 'delete' && typeof itemIndex === 'number') {
            nextHtml = deleteTaskItemInHtml(resolved.content, itemIndex);
          } else if (op === 'add' && typeof text === 'string' && text.trim()) {
            nextHtml = addTaskItemToHtml(resolved.content, text);
          }
          modifiedHtmlMap.set(resolved.id, nextHtml);
        }
      }
    }

    // 2. Fallback: process legacy pending toggles/adds if any exist
    if (widgetPlugin.getPendingToggles) {
      const res = await widgetPlugin.getPendingToggles();
      const toggles = res?.toggles;
      if (Array.isArray(toggles) && toggles.length > 0) {
        for (const t of toggles) {
          const resolved = resolveWorkingContent(t.noteId || '');
          if (!resolved) continue;
          const nextHtml = toggleTaskItemInHtml(resolved.content, t.itemIndex, Boolean(t.checked));
          modifiedHtmlMap.set(resolved.id, nextHtml);
        }
      }
    }

    if (widgetPlugin.getPendingAdds) {
      const addsRes = await widgetPlugin.getPendingAdds();
      const adds = addsRes?.adds;
      if (Array.isArray(adds) && adds.length > 0) {
        for (const a of adds) {
          if (!a.text || !a.text.trim()) continue;
          const resolved = resolveWorkingContent(a.noteId || '');
          if (!resolved) continue;
          const nextHtml = addTaskItemToHtml(resolved.content, a.text);
          modifiedHtmlMap.set(resolved.id, nextHtml);
        }
      }
    }

    // Apply all net content changes atomically per note
    for (const [id, finalHtml] of modifiedHtmlMap.entries()) {
      const original = notes.find((n) => n.id === id);
      if (original && finalHtml !== original.content) {
        updateNote(id, {
          content: finalHtml,
          plainTextPreview: computeChecklistPreview(finalHtml),
          updatedAt: Date.now(),
        });
      }
    }
  } catch (err) {
    console.warn('Failed to apply pending widget actions:', err);
  }
}

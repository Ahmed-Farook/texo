import { Folder, Note } from '../types/note';

const NOTES_STORAGE_KEY = 'apple_notes_data_v1';
const NOTES_BACKUP_STORAGE_KEY = 'apple_notes_backup_v1';
const FOLDERS_STORAGE_KEY = 'apple_notes_folders_v1';

export const SYSTEM_FOLDERS: Folder[] = [
  { id: 'default', name: 'Notes', isSystem: true },
  { id: 'checklists', name: 'Checklists', isSystem: true },
  { id: 'trash', name: 'Recently Deleted', isSystem: true },
];

export const INITIAL_FOLDERS: Folder[] = [
  { id: 'work', name: 'Work & Projects' },
  { id: 'personal', name: 'Personal & Ideas' },
];

export const INITIAL_NOTES: Note[] = [
  {
    id: 'note-welcome',
    title: 'Welcome to texō',
    content: `<h1>Welcome to texō 👋</h1><p>We're so glad you're here! This is your clean, distraction-free space for capturing quick thoughts, daily to-dos, and big ideas.</p><p>Everything you write is saved automatically to your device with <strong>0ms local speed</strong>, works completely offline, and can be synced across all your devices with <strong>Zero-Knowledge End-to-End Encryption</strong>.</p><h3>✨ A few things to try:</h3><ul data-type="taskList"><li data-checked="true" data-type="taskItem"><label><input type="checkbox" checked="checked"><span></span></label><div><p>Check off items on your to-do lists</p></div></li><li data-checked="false" data-type="taskItem"><label><input type="checkbox"><span></span></label><div><p>Create a fresh note with <strong>New Note</strong> (or press <code>Cmd+N</code> / <code>Ctrl+N</code>)</p></div></li><li data-checked="false" data-type="taskItem"><label><input type="checkbox"><span></span></label><div><p>Organize your ideas into custom folders in the sidebar</p></div></li><li data-checked="false" data-type="taskItem"><label><input type="checkbox"><span></span></label><div><p>Sign in to sync seamlessly between macOS, Windows, Android, and Web</p></div></li></ul><p></p><blockquote><p>“Simplicity is the ultimate sophistication.” — Make yourself at home and happy writing! 💛</p></blockquote>`,
    plainTextPreview: "We're so glad you're here! This is your clean, distraction-free space for capturing quick thoughts, daily to-dos, and big ideas...",
    folderId: 'default',
    isPinned: true,
    createdAt: Date.now(),
    updatedAt: Date.now(),
  },
];

/**
 * Checks whether the note's HTML content contains normal note elements (paragraphs outside tasks,
 * subheadings, blockquotes, tables, images, code, etc.). If it has ANY normal note content,
 * it is a rich text note and can NEVER be treated as a pure checklist.
 */
export function hasRichNoteContent(content?: string | null): boolean {
  if (!content) return false;
  // If it has images, tables, canvas, blockquotes, code blocks, or math formulas
  if (/<(img|table|canvas|blockquote|pre|code)/i.test(content) || content.includes('data-type="mathBlock"')) {
    return true;
  }
  // If it has subheadings (h2, h3, h4, h5, h6)
  if (/<h[2-6]/i.test(content)) {
    return true;
  }
  // If it has bullet or numbered lists (not taskList)
  if (/<ol/i.test(content) || (/<ul/i.test(content) && !content.includes('data-type="taskList"'))) {
    return true;
  }
  // Check for normal paragraphs outside task items
  const withoutTasks = content
    .replace(/<li[^>]*data-type=["']taskItem["'][\s\S]*?<\/li>/gi, '')
    .replace(/<h1>[\s\S]*?<\/h1>/gi, '')
    .replace(/<ul[^>]*data-type=["']taskList["'][\s\S]*?<\/ul>/gi, '');

  const cleanRemainingText = withoutTasks.replace(/<[^>]*>/g, '').trim();
  return cleanRemainingText.length > 0;
}

/**
 * Accurately determines if a note is a checklist.
 * A note is a checklist IF AND ONLY IF it was created as a checklist (`isChecklist === true`)
 * and does NOT contain rich text note content.
 * Having checkboxes inside a normal rich text note or erasing drawings does NOT convert it into a checklist.
 */
export function isChecklistNote(note?: Partial<Note> | null): boolean {
  if (!note) return false;

  // 1. Any note with rich text content (paragraphs, headings, images, tables, etc.) is NEVER a checklist
  if (hasRichNoteContent(note.content)) {
    return false;
  }

  // 2. Any note that has drawing strokes is NEVER a checklist
  if (note.drawingStrokes && note.drawingStrokes.length > 2 && note.drawingStrokes !== '[]' && note.drawingStrokes !== '{}') {
    return false;
  }

  // 3. Strict explicit flag check: if isChecklist is explicitly false, it is NEVER a checklist!
  if (note.isChecklist === false) {
    return false;
  }

  // 4. If explicitly marked as checklist
  if (note.isChecklist === true) {
    return true;
  }

  // 5. Legacy notes: only consider checklist if in 'checklists' folder AND content actually has a taskList
  return note.folderId === 'checklists' && Boolean(note.content && note.content.includes('data-type="taskList"'));
}

export const loadStoredNotes = (): Note[] => {
  try {
    let data = localStorage.getItem(NOTES_STORAGE_KEY);
    if (!data) {
      data = localStorage.getItem(NOTES_BACKUP_STORAGE_KEY);
    }
    if (!data) return INITIAL_NOTES;
    const parsed = JSON.parse(data);
    if (!Array.isArray(parsed) || parsed.length === 0) return INITIAL_NOTES;

    // Migrate users who still have the legacy 3 default sample notes
    const isLegacyDefault =
      parsed.length === 3 &&
      parsed.some((n: any) => n.id === 'note-roadmap') &&
      parsed.some((n: any) => n.id === 'note-weekend-ideas');
    if (isLegacyDefault) {
      saveStoredNotes(INITIAL_NOTES);
      return INITIAL_NOTES;
    }

    // Try reading backup notes to recover any notes that got corrupted into empty checklists
    let backupNotesMap = new Map<string, Note>();
    try {
      const backupData = localStorage.getItem(NOTES_BACKUP_STORAGE_KEY);
      if (backupData) {
        const backupParsed = JSON.parse(backupData);
        if (Array.isArray(backupParsed)) {
          backupParsed.forEach((bn: Note) => {
            if (bn && bn.id && bn.content && bn.content.length > 60) {
              backupNotesMap.set(bn.id, bn);
            }
          });
        }
      }
    } catch (_) {}

    // Migrate legacy notes, ensure trashed notes are unpinned, and recover notes
    let hasMigration = false;
    const migratedNotes = parsed.map((n: Note) => {
      let updated = n;
      if (n.folderId === 'quick' || n.folderId === 'all' || !n.folderId) {
        hasMigration = true;
        updated = { ...updated, folderId: 'default' };
      }
      if (updated.isTrash && updated.isPinned) {
        hasMigration = true;
        updated = { ...updated, isPinned: false };
      }

      // Check if this note was accidentally wiped into an empty checklist
      const isPureEmptyChecklist = Boolean(
        updated.content &&
        updated.content.includes('<ul data-type="taskList"') &&
        (!updated.plainTextPreview || updated.plainTextPreview.includes('0 tasks completed')) &&
        !hasRichNoteContent(updated.content)
      );

      // Attempt recovery from previousContentBackup or backupNotesMap
      if (isPureEmptyChecklist && updated.title && updated.title !== 'To-Do List' && updated.title !== 'New Note') {
        if (updated.previousContentBackup && updated.previousContentBackup.length > 50) {
          hasMigration = true;
          updated = {
            ...updated,
            content: updated.previousContentBackup,
            isChecklist: false,
            folderId: updated.folderId === 'checklists' ? 'default' : updated.folderId,
          };
        } else {
          const backupVersion = backupNotesMap.get(updated.id);
          if (backupVersion && backupVersion.content && backupVersion.content.length > updated.content.length) {
            hasMigration = true;
            updated = {
              ...updated,
              content: backupVersion.content,
              drawingStrokes: updated.drawingStrokes || backupVersion.drawingStrokes,
              isChecklist: false,
              folderId: updated.folderId === 'checklists' ? 'default' : updated.folderId,
            };
          }
        }
      }

      // Strict isChecklist flag enforcement
      const hasDrawingStrokes = Boolean(
        updated.drawingStrokes &&
        updated.drawingStrokes.length > 2 &&
        updated.drawingStrokes !== '[]' &&
        updated.drawingStrokes !== '{}'
      );
      const isRich = hasRichNoteContent(updated.content);
      const isPureChecklist = Boolean(
        updated.content &&
        updated.content.includes('data-type="taskList"') &&
        !isRich &&
        !hasDrawingStrokes
      );

      // Auto-recover any note that has rich content, drawing strokes, or non-checklist title
      if ((hasDrawingStrokes || isRich || !isPureChecklist) && (updated.isChecklist === true || updated.folderId === 'checklists')) {
        hasMigration = true;
        updated = {
          ...updated,
          isChecklist: false,
          folderId: updated.folderId === 'checklists' ? 'default' : (updated.folderId || 'default'),
        };
      } else if (updated.isChecklist === undefined) {
        hasMigration = true;
        updated = {
          ...updated,
          isChecklist: isPureChecklist && updated.folderId === 'checklists',
        };
      }

      // Migrate older notes where title was saved as <h3> to <h1>
      if (typeof updated.content === 'string' && /^\s*<h3>([\s\S]*?)<\/h3>/i.test(updated.content)) {
        hasMigration = true;
        updated = {
          ...updated,
          content: updated.content.replace(/^\s*<h3>([\s\S]*?)<\/h3>/i, '<h1>$1</h1>'),
        };
      }
      return updated;
    });

    if (hasMigration) {
      saveStoredNotes(migratedNotes);
      return migratedNotes;
    }

    return parsed;
  } catch (err) {
    console.error('Failed to load notes from localStorage', err);
    return INITIAL_NOTES;
  }
};

export const saveStoredNotes = (notes: Note[]): void => {
  try {
    const raw = JSON.stringify(notes);
    localStorage.setItem(NOTES_STORAGE_KEY, raw);
    if (notes.length > 0) {
      try {
        localStorage.setItem(NOTES_BACKUP_STORAGE_KEY, raw);
      } catch (_) {}
    }
  } catch (err) {
    console.error('Failed to save notes to localStorage', err);
  }
};

export const loadStoredFolders = (): Folder[] => {
  try {
    const data = localStorage.getItem(FOLDERS_STORAGE_KEY);
    if (!data) return INITIAL_FOLDERS;
    const parsed = JSON.parse(data);
    if (!Array.isArray(parsed)) return INITIAL_FOLDERS;
    const sanitized = parsed.filter(
      (f: any) =>
        Boolean(
          f &&
          typeof f.id === 'string' &&
          typeof f.name === 'string' &&
          f.name.trim() &&
          f.id !== 'default' &&
          f.id !== 'checklists' &&
          f.id !== 'trash'
        )
    );
    return sanitized.length > 0 ? sanitized : INITIAL_FOLDERS;
  } catch (err) {
    console.error('Failed to load folders from localStorage', err);
    return INITIAL_FOLDERS;
  }
};

export const saveStoredFolders = (folders: Folder[]): void => {
  try {
    localStorage.setItem(FOLDERS_STORAGE_KEY, JSON.stringify(folders));
  } catch (err) {
    console.error('Failed to save folders to localStorage', err);
  }
};


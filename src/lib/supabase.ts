import { createClient } from '@supabase/supabase-js';

export const SUPABASE_URL =
  import.meta.env.VITE_SUPABASE_URL || 'https://fbkwajfibdbqbzfhaycx.supabase.co';
export const SUPABASE_ANON_KEY =
  import.meta.env.VITE_SUPABASE_ANON_KEY || 'sb_publishable_ID7MzXfTFabTtFPApRsYbg_SgpkigOH';

export const supabase = createClient(SUPABASE_URL, SUPABASE_ANON_KEY, {
  auth: {
    persistSession: true,
    autoRefreshToken: true,
    detectSessionInUrl: true,
    // Override browser LockManager (navigator.locks) to eliminate the 30-40s deadlock on launch
    lock: async (_name, _acquireTimeout, fn) => {
      return await fn();
    },
  },
});


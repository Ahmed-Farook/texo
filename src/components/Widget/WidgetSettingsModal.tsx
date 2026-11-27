import React, { useEffect, useState } from 'react';
import { createPortal } from 'react-dom';
import { X, SlidersHorizontal, Check, Eye, Sparkles } from 'lucide-react';
import { triggerHaptic } from '../../utils/haptics';
import {
  WIDGET_BG_PRESETS,
  WidgetAppearanceConfig,
  DEFAULT_WIDGET_APPEARANCE,
  getWidgetAppearance,
  setWidgetAppearance,
} from '../../utils/widgetSync';
import { useNotes } from '../../store/NotesContext';

interface WidgetSettingsModalProps {
  isOpen: boolean;
  onClose: () => void;
}

const WALLPAPER_SWATCHES = [
  { id: 'dawn', label: 'Warm Dawn', style: 'linear-gradient(135deg, #F6D365 0%, #FDA085 100%)' },
  { id: 'night', label: 'Night Sky', style: 'linear-gradient(135deg, #0F2027 0%, #203A43 50%, #2C5364 100%)' },
  { id: 'aurora', label: 'Aurora', style: 'linear-gradient(135deg, #43C6AC 0%, #191654 100%)' },
  { id: 'minimal', label: 'Soft Mist', style: 'linear-gradient(135deg, #E2E8F0 0%, #CBD5E1 100%)' },
];

export const WidgetSettingsModal: React.FC<WidgetSettingsModalProps> = ({ isOpen, onClose }) => {
  const { theme } = useNotes();
  const [config, setConfig] = useState<WidgetAppearanceConfig>(DEFAULT_WIDGET_APPEARANCE);
  const [wallpaperIdx, setWallpaperIdx] = useState(1);

  useEffect(() => {
    if (!isOpen) return;
    getWidgetAppearance().then((loaded) => setConfig(loaded));
  }, [isOpen]);

  if (!isOpen) return null;

  const updateConfig = (partial: Partial<WidgetAppearanceConfig>) => {
    const next: WidgetAppearanceConfig = { ...config, ...partial };
    setConfig(next);
    setWidgetAppearance(next);
  };

  const presetMeta = WIDGET_BG_PRESETS.find((p) => p.id === config.preset) || WIDGET_BG_PRESETS[0];
  const resolvedBg =
    config.preset === 'auto'
      ? theme === 'dark'
        ? '#16202A'
        : '#FAF6EF'
      : presetMeta.swatch;
  const resolvedBorder =
    config.preset === 'auto'
      ? theme === 'dark'
        ? '#223344'
        : '#DFD3C0'
      : presetMeta.border;
  const isLightFg =
    config.textMode === 'light'
      ? true
      : config.textMode === 'dark'
      ? false
      : config.preset === 'auto'
      ? theme === 'dark'
      : !presetMeta.isLightSurface;

  const primaryText = isLightFg ? '#F2F6FA' : '#1C2B36';
  const mutedText = isLightFg ? '#B4C6D6' : '#5A6E82';

  const hexToRgba = (hex: string, alphaPercent: number) => {
    const clean = hex.replace('#', '');
    const r = parseInt(clean.slice(0, 2), 16);
    const g = parseInt(clean.slice(2, 4), 16);
    const b = parseInt(clean.slice(4, 6), 16);
    return `rgba(${r}, ${g}, ${b}, ${Math.max(0, Math.min(1, alphaPercent / 100))})`;
  };

  const modalContent = (
    <div className="fixed inset-0 z-[160] flex items-center justify-center p-4 bg-black/55 backdrop-blur-sm animate-in fade-in duration-150 [app-region:no-drag]">
      <div
        className="w-full max-w-sm max-h-[90vh] bg-[#FAF6EF] dark:bg-[#16202A] rounded-2xl shadow-2xl border border-[#DFD3C0] dark:border-[#223344] overflow-hidden flex flex-col text-[#1C2B36] dark:text-[#E8EEF3] animate-in zoom-in-95 duration-150"
        role="dialog"
        aria-modal="true"
      >
        {/* Header */}
        <div className="flex items-center justify-between px-5 py-3.5 border-b border-[#DFD3C0] dark:border-[#223344] bg-[#EFE7D8]/85 dark:bg-[#131B24]/85 shrink-0">
          <div className="flex items-center space-x-2">
            <SlidersHorizontal size={16} className="text-[#213A50] dark:text-[#7EAAD0]" />
            <h3 className="text-sm font-semibold">Home Screen Widgets</h3>
          </div>
          <button
            type="button"
            onClick={() => {
              triggerHaptic();
              onClose();
            }}
            className="text-[#5A6E82] hover:text-[#1C2B36] dark:text-[#8AA0B4] dark:hover:text-[#E8EEF3] p-1 rounded-full transition-colors"
            aria-label="Close"
          >
            <X size={16} />
          </button>
        </div>

        {/* Scrollable Content */}
        <div className="p-4 space-y-4 overflow-y-auto">
          {/* Live Preview Card Over Simulated Wallpaper */}
          <div className="space-y-2">
            <div className="flex items-center justify-between text-[11px] font-semibold uppercase tracking-wider text-[#5A6E82] dark:text-[#8AA0B4]">
              <span className="flex items-center gap-1">
                <Eye size={12} /> Live Wallpaper Preview
              </span>
              <div className="flex items-center gap-1.5">
                {WALLPAPER_SWATCHES.map((wp, idx) => (
                  <button
                    key={wp.id}
                    type="button"
                    onClick={() => {
                      triggerHaptic();
                      setWallpaperIdx(idx);
                    }}
                    title={wp.label}
                    className={`w-4 h-4 rounded-full border transition-transform ${
                      wallpaperIdx === idx
                        ? 'scale-110 border-white ring-2 ring-[#213A50] dark:ring-[#7EAAD0]'
                        : 'border-black/20 opacity-75 hover:opacity-100'
                    }`}
                    style={{ background: wp.style }}
                  />
                ))}
              </div>
            </div>

            <div
              className="rounded-2xl p-4 shadow-inner transition-all"
              style={{ background: WALLPAPER_SWATCHES[wallpaperIdx].style }}
            >
              <div
                className="rounded-[20px] p-3.5 transition-all"
                style={{
                  backgroundColor: hexToRgba(resolvedBg, config.opacity),
                  border: config.showBorder
                    ? `1.2px solid ${hexToRgba(resolvedBorder, Math.max(38, config.opacity) / 100 * 100)}`
                    : '1.2px solid transparent',
                }}
              >
                <div className="flex items-center justify-between pb-2 mb-2 border-b border-current/15" style={{ color: primaryText }}>
                  <span className="text-sm font-bold truncate">Daily Tasks</span>
                  <span className="w-6 h-6 rounded-full bg-[#213A50] dark:bg-[#345A7D] text-white flex items-center justify-center text-sm font-bold leading-none">
                    +
                  </span>
                </div>
                <div className="space-y-2 text-xs">
                  <div className="flex items-center justify-between" style={{ color: primaryText }}>
                    <div className="flex items-center gap-2">
                      <span
                        className="w-3.5 h-3.5 rounded-full border-[1.6px]"
                        style={{ borderColor: isLightFg ? '#7EAAD0' : '#213A50' }}
                      />
                      <span>Review notes before call</span>
                    </div>
                    <X size={12} style={{ color: mutedText }} />
                  </div>
                  <div className="flex items-center justify-between" style={{ color: mutedText }}>
                    <div className="flex items-center gap-2 line-through">
                      <span className="w-3.5 h-3.5 rounded-full bg-[#213A50] dark:bg-[#345A7D] text-white flex items-center justify-center">
                        <Check size={9} strokeWidth={3} />
                      </span>
                      <span>Sync across devices</span>
                    </div>
                    <X size={12} style={{ color: mutedText }} />
                  </div>
                </div>
              </div>
            </div>
          </div>

          {/* Opacity Slider */}
          <div className="space-y-1.5">
            <div className="flex items-center justify-between text-xs font-semibold">
              <span>Background Opacity</span>
              <span className="font-mono text-[#213A50] dark:text-[#7EAAD0]">{config.opacity}%</span>
            </div>
            <input
              type="range"
              min={0}
              max={100}
              step={5}
              value={config.opacity}
              onChange={(e) => updateConfig({ opacity: Number(e.target.value) })}
              className="w-full accent-[#213A50] dark:accent-[#7EAAD0] cursor-pointer"
            />
            <div className="flex justify-between text-[10px] text-[#6E8092] dark:text-[#8AA0B4]">
              <button type="button" onClick={() => updateConfig({ opacity: 0 })} className="hover:underline">
                0% Transparent
              </button>
              <button type="button" onClick={() => updateConfig({ opacity: 60 })} className="hover:underline">
                60% Glass
              </button>
              <button type="button" onClick={() => updateConfig({ opacity: 100 })} className="hover:underline">
                100% Solid
              </button>
            </div>
          </div>

          {/* Background Tint Presets */}
          <div className="space-y-2">
            <span className="text-xs font-semibold block">Widget Tint</span>
            <div className="grid grid-cols-3 gap-1.5">
              {WIDGET_BG_PRESETS.map((preset) => {
                const selected = config.preset === preset.id;
                return (
                  <button
                    key={preset.id}
                    type="button"
                    onClick={() => {
                      triggerHaptic();
                      updateConfig({ preset: preset.id });
                    }}
                    className={`flex items-center gap-1.5 px-2.5 py-2 rounded-xl border text-[11px] font-medium transition-all text-left ${
                      selected
                        ? 'border-[#213A50] dark:border-[#7EAAD0] bg-[#213A50]/10 dark:bg-[#7EAAD0]/15 ring-1 ring-[#213A50] dark:ring-[#7EAAD0]'
                        : 'border-[#DFD3C0] dark:border-[#223344] bg-[#EFE7D8]/60 dark:bg-[#1C2733]/60 hover:bg-[#E5DCCB] dark:hover:bg-[#223344]'
                    }`}
                  >
                    <span
                      className="w-3.5 h-3.5 rounded-full shrink-0 border"
                      style={{
                        backgroundColor:
                          preset.id === 'auto'
                            ? theme === 'dark'
                              ? '#16202A'
                              : '#FAF6EF'
                            : preset.swatch,
                        borderColor: preset.border,
                      }}
                    />
                    <span className="truncate">{preset.label}</span>
                  </button>
                );
              })}
            </div>
          </div>

          {/* Text & Icon Contrast */}
          <div className="space-y-1.5">
            <span className="text-xs font-semibold block">Text &amp; Icon Contrast</span>
            <div className="grid grid-cols-3 gap-1.5 bg-[#EFE7D8] dark:bg-[#131B24] p-1 rounded-xl border border-[#DFD3C0] dark:border-[#223344]">
              {(
                [
                  { id: 'auto', label: 'Auto' },
                  { id: 'light', label: 'Light Ink' },
                  { id: 'dark', label: 'Dark Ink' },
                ] as const
              ).map((opt) => (
                <button
                  key={opt.id}
                  type="button"
                  onClick={() => {
                    triggerHaptic();
                    updateConfig({ textMode: opt.id });
                  }}
                  className={`py-1.5 rounded-lg text-[11px] font-semibold transition-all ${
                    config.textMode === opt.id
                      ? 'bg-[#213A50] dark:bg-[#345A7D] text-white shadow-xs'
                      : 'text-[#5A6E82] dark:text-[#8AA0B4] hover:text-[#1C2B36] dark:hover:text-[#E8EEF3]'
                  }`}
                >
                  {opt.label}
                </button>
              ))}
            </div>
          </div>

          {/* Card Outline Border Toggle */}
          <div className="flex items-center justify-between px-3 py-2.5 rounded-xl bg-[#EFE7D8]/70 dark:bg-[#131B24]/70 border border-[#DFD3C0] dark:border-[#223344]">
            <div>
              <p className="text-xs font-semibold">Show Card Outline</p>
              <p className="text-[10px] text-[#5A6E82] dark:text-[#8AA0B4]">
                Turn off for a seamless floating wallpaper look
              </p>
            </div>
            <button
              type="button"
              role="switch"
              aria-checked={config.showBorder}
              onClick={() => {
                triggerHaptic();
                updateConfig({ showBorder: !config.showBorder });
              }}
              className={`w-10 h-6 rounded-full transition-colors p-0.5 flex items-center ${
                config.showBorder
                  ? 'bg-[#213A50] dark:bg-[#345A7D] justify-end'
                  : 'bg-[#D2C5B0] dark:bg-[#263747] justify-start'
              }`}
            >
              <span className="w-5 h-5 rounded-full bg-white shadow-sm block" />
            </button>
          </div>
        </div>

        {/* Footer */}
        <div className="px-4 py-3 border-t border-[#DFD3C0] dark:border-[#223344] bg-[#EFE7D8]/60 dark:bg-[#131B24]/60 flex items-center justify-between shrink-0">
          <span className="text-[10px] text-[#5A6E82] dark:text-[#8AA0B4] flex items-center gap-1">
            <Sparkles size={11} /> Applies live to all widgets
          </span>
          <button
            type="button"
            onClick={() => {
              triggerHaptic();
              onClose();
            }}
            className="px-4 py-1.5 rounded-xl bg-[#213A50] dark:bg-[#345A7D] text-white text-xs font-semibold shadow-xs active:scale-95 transition-transform"
          >
            Done
          </button>
        </div>
      </div>
    </div>
  );

  if (typeof document !== 'undefined') {
    return createPortal(modalContent, document.body);
  }
  return modalContent;
};

export const workbenchPanelClass = 'flex min-h-[84px] min-w-0 flex-col rounded-none border border-slate-200 bg-white p-3 shadow-sm'
export const workbenchFieldShellClass = 'mt-2 flex min-h-9 items-center border border-slate-200 bg-white'
export const workbenchCompactButtonClass = '!h-9 !min-h-9 !w-full !justify-between !rounded-none !bg-transparent !px-3 !text-slate-900 !ring-0 shadow-none hover:!bg-transparent focus:!ring-0'
export const workbenchMenuFieldShellClass = workbenchFieldShellClass
export const workbenchMenuTriggerClass = '!h-9 !min-h-9 !w-full !justify-between !rounded-none !bg-transparent !px-3 !text-slate-900 !ring-0 shadow-none hover:!bg-transparent focus:!ring-0'
export const workbenchMenuItemClass = 'rounded-none p-2 text-sm text-slate-900 outline-none before:rounded-none before:bg-transparent transition-none before:transition-none hover:bg-slate-100 hover:text-slate-950 hover:before:bg-slate-100 active:bg-slate-100 active:text-slate-950 active:before:bg-slate-100 focus:bg-slate-100 focus:text-slate-950 focus:before:bg-slate-100 focus-visible:bg-slate-100 focus-visible:text-slate-950 focus-visible:before:bg-slate-100 data-highlighted:bg-slate-100 data-highlighted:text-slate-950 data-highlighted:before:bg-slate-100 data-[state=open]:bg-slate-100 data-[state=open]:text-slate-950 data-[state=open]:before:bg-slate-100 data-[state=checked]:bg-slate-100 data-[state=checked]:text-slate-950 data-[state=checked]:before:bg-slate-100'

export const workbenchSelectUi = {
  base: 'w-full rounded-none bg-transparent text-slate-900 ring-0 shadow-none hover:bg-transparent focus:ring-0',
  content: 'rounded-none bg-white text-slate-900 ring ring-slate-200 shadow-xl overflow-hidden',
  viewport: 'divide-y-0 bg-white',
  group: 'p-1',
  item: workbenchMenuItemClass,
  itemLabel: 'truncate text-slate-900',
  itemDescription: 'truncate text-slate-600',
  itemTrailing: 'ms-auto inline-flex items-center text-slate-700',
  itemTrailingIcon: 'size-5 text-slate-700 opacity-100',
  trailingIcon: 'text-slate-500',
  leadingIcon: 'text-slate-500'
}

export const workbenchDropdownUi = {
  content: 'min-w-60 rounded-none border border-slate-200 bg-white p-2 shadow-xl shadow-slate-900/10',
  item: workbenchMenuItemClass,
  itemLabel: 'truncate text-slate-900',
  itemDescription: 'truncate text-slate-600',
  itemTrailingIcon: 'text-slate-700',
  itemLeadingIcon: 'text-slate-500',
  label: 'px-2 py-1 text-xs font-semibold text-slate-700',
  separator: 'my-1 h-px bg-slate-200'
}

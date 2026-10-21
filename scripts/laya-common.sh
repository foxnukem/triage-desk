# Shared by the Laya scripts: a sparse checkout of the laya repo at a pinned commit.
LAYA_COMMIT=4ac226d5ee88ed6df8db288a05c766d7a34ee682
LAYA_SRC=${LAYA_SRC:-${TMPDIR:-/tmp}/laya-src-${LAYA_COMMIT:0:7}}

laya_checkout() {
  if [ ! -d "$LAYA_SRC/.git" ]; then
    git clone --filter=blob:none --sparse -q https://github.com/NandhaKishorM/laya.git "$LAYA_SRC"
  fi
  git -C "$LAYA_SRC" checkout -q "$LAYA_COMMIT"
  git -C "$LAYA_SRC" sparse-checkout set laya-java laya scripts
}

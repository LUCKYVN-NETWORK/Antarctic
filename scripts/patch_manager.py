#!/usr/bin/env python3
import sys, os, shutil, subprocess
from pathlib import Path

root = Path(__file__).resolve().parent.parent
server_dir = root / "Antarctic-Server"
mc_patches = server_dir / "minecraft-patches"
paper_patches = server_dir / "paper-patches"
api_src = root / "Antarctic-API/src"
server_src = root / "Antarctic-Server/src"

def clean(repo):
    git_dir = repo / ".git"
    if not git_dir.is_dir():
        sys.exit(f"Run setupSources first: {repo}")
    status = subprocess.run(["git", "-C", str(repo), "status", "--porcelain"], capture_output=True, text=True, check=True).stdout.strip()
    if status:
        sys.exit(f"Commit or stash source edits first: {repo}")
    if (git_dir / "rebase-apply").is_dir() or (git_dir / "rebase-merge").is_dir():
        sys.exit(f"Finish or abort the active rebase/patch application: {repo}")

def apply():
    clean(api_src)
    clean(server_src)
    
    api_list = sorted(paper_patches.glob("api-*.patch"))
    server_list = sorted(
        list(mc_patches.glob("*.patch")) +
        [p for p in paper_patches.glob("*.patch") if not p.name.startswith("api-")],
        key=lambda p: p.name
    )
    
    subprocess.run(["git", "-C", str(api_src), "reset", "--hard", "antarctic-upstream"], check=True)
    if api_list:
        subprocess.run(["git", "-C", str(api_src), "-c", "commit.gpgsign=false", "am", "--3way"] + [str(p) for p in api_list], check=True)
        
    subprocess.run(["git", "-C", str(server_src), "reset", "--hard", "antarctic-upstream"], check=True)
    if server_list:
        subprocess.run(["git", "-C", str(server_src), "-c", "commit.gpgsign=false", "am", "--3way"] + [str(p) for p in server_list], check=True)
    print("Patches applied successfully.")

def rebuild():
    clean(api_src)
    clean(server_src)
    
    mc_patches.mkdir(parents=True, exist_ok=True)
    paper_patches.mkdir(parents=True, exist_ok=True)
    
    import tempfile
    with tempfile.TemporaryDirectory() as tmp:
        tmp_path = Path(tmp)
        tmp_api = tmp_path / "API"
        tmp_server = tmp_path / "Server"
        tmp_api.mkdir()
        tmp_server.mkdir()
        
        subprocess.run(["git", "-C", str(api_src), "format-patch", "--no-stat", "--zero-commit", "--no-signature", "-o", str(tmp_api), "antarctic-upstream..HEAD"], check=True)
        subprocess.run(["git", "-C", str(server_src), "format-patch", "--no-stat", "--zero-commit", "--no-signature", "-o", str(tmp_server), "antarctic-upstream..HEAD"], check=True)
        
        # Clear existing
        for p in paper_patches.glob("api-*.patch"):
            p.unlink()
        for p in paper_patches.glob("[0-9]*.patch"):
            p.unlink()
        for p in mc_patches.glob("*.patch"):
            p.unlink()
            
        for p in sorted(tmp_api.glob("*.patch")):
            shutil.copy2(p, paper_patches / f"api-{p.name}")
            
        for p in sorted(tmp_server.glob("*.patch")):
            content = p.read_text(encoding="utf-8", errors="ignore")
            diff_lines = [l for l in content.splitlines() if l.startswith("diff --git ")]
            nms_files = [l for l in diff_lines if "net/minecraft/server" in l]
            cb_files = [l for l in diff_lines if "org/bukkit" in l or "com/destroystokyo" in l or "me/stella" in l]
            
            if nms_files and not cb_files:
                shutil.copy2(p, mc_patches / p.name)
            elif cb_files and not nms_files:
                shutil.copy2(p, paper_patches / p.name)
            elif not nms_files and not cb_files:
                shutil.copy2(p, paper_patches / p.name)
            else:
                subject = [l for l in content.splitlines() if l.startswith("Subject: ")][0].lower()
                if "config" in subject or "toml" in subject or "branding" in subject:
                    shutil.copy2(p, paper_patches / p.name)
                elif "backport" in subject or "paper" in subject:
                    shutil.copy2(p, paper_patches / p.name)
                elif len(nms_files) >= len(cb_files):
                    shutil.copy2(p, mc_patches / p.name)
                else:
                    shutil.copy2(p, paper_patches / p.name)
    print("Patches rebuilt successfully.")

if __name__ == "__main__":
    if len(sys.argv) < 2 or sys.argv[1] not in ("apply", "rebuild"):
        sys.exit("Usage: patch_manager.py apply | rebuild")
    if sys.argv[1] == "apply":
        apply()
    else:
        rebuild()

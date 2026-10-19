#!/usr/bin/env python3
import sys, os, shutil, subprocess
from pathlib import Path

root = Path(__file__).resolve().parent.parent

api_dir = root / "Antarctic-API"
server_dir = root / "Antarctic-Server"

api_mc_patches = api_dir / "minecraft-patches"
api_paper_patches = api_dir / "paper-patches"

server_mc_patches = server_dir / "minecraft-patches"
server_paper_patches = server_dir / "paper-patches"

api_src = api_dir / "src"
server_src = server_dir / "src"

def get_git_args(repo):
    git_dir = repo / ".git"
    if not git_dir.exists():
        alt = repo.parent / ".git-sources"
        if alt.exists():
            return ["--git-dir", str(alt), "--work-tree", str(repo)]
    return ["-C", str(repo)]

def git(repo, args, **kwargs):
    cmd = ["git"] + get_git_args(repo) + args
    return subprocess.run(cmd, **kwargs)

def get_git_dir(repo):
    if (repo / ".git").exists():
        return repo / ".git"
    if (repo.parent / ".git-sources").exists():
        return repo.parent / ".git-sources"
    return None

def clean(repo):
    git_dir = get_git_dir(repo)
    if not git_dir or not git_dir.is_dir():
        sys.exit(f"Run setupSources first: {repo}")
    status = git(repo, ["status", "--porcelain"], capture_output=True, text=True, check=True).stdout.strip()
    if status:
        sys.exit(f"Commit or stash source edits first: {repo}")
    if (git_dir / "rebase-apply").is_dir() or (git_dir / "rebase-merge").is_dir():
        sys.exit(f"Finish or abort the active rebase/patch application: {repo}")

def apply():
    clean(api_src)
    clean(server_src)

    api_list = sorted(
        list(api_mc_patches.glob("*.patch")) + list(api_paper_patches.glob("*.patch")),
        key=lambda p: p.name
    )
    server_list = sorted(
        list(server_mc_patches.glob("*.patch")) + list(server_paper_patches.glob("*.patch")),
        key=lambda p: p.name
    )

    git(api_src, ["reset", "--hard", "antarctic-upstream"], check=True)
    if api_list:
        git(api_src, ["-c", "commit.gpgsign=false", "am", "--3way"] + [str(p) for p in api_list], check=True)

    git(server_src, ["reset", "--hard", "antarctic-upstream"], check=True)
    if server_list:
        git(server_src, ["-c", "commit.gpgsign=false", "am", "--3way"] + [str(p) for p in server_list], check=True)
    print("Patches applied successfully.")

def rebuild():
    clean(api_src)
    clean(server_src)

    api_mc_patches.mkdir(parents=True, exist_ok=True)
    api_paper_patches.mkdir(parents=True, exist_ok=True)
    server_mc_patches.mkdir(parents=True, exist_ok=True)
    server_paper_patches.mkdir(parents=True, exist_ok=True)

    import tempfile
    with tempfile.TemporaryDirectory() as tmp:
        tmp_path = Path(tmp)
        tmp_api = tmp_path / "API"
        tmp_server = tmp_path / "Server"
        tmp_api.mkdir()
        tmp_server.mkdir()

        git(api_src, ["format-patch", "--no-stat", "--zero-commit", "--no-signature", "-o", str(tmp_api), "antarctic-upstream..HEAD"], check=True)
        git(server_src, ["format-patch", "--no-stat", "--zero-commit", "--no-signature", "-o", str(tmp_server), "antarctic-upstream..HEAD"], check=True)

        for p in api_mc_patches.glob("*.patch"):
            p.unlink()
        for p in api_paper_patches.glob("*.patch"):
            p.unlink()
        for p in server_mc_patches.glob("*.patch"):
            p.unlink()
        for p in server_paper_patches.glob("*.patch"):
            p.unlink()

        def sort_and_copy(patch_file, mc_dir, paper_dir):
            content = patch_file.read_text(encoding="utf-8", errors="ignore")
            diff_lines = [l for l in content.splitlines() if l.startswith("diff --git ")]
            nms_files = [l for l in diff_lines if "net/minecraft/server" in l]
            cb_files = [l for l in diff_lines if "org/bukkit" in l or "com/destroystokyo" in l or "me/stella" in l]

            if nms_files and not cb_files:
                shutil.copy2(patch_file, mc_dir / patch_file.name)
            elif cb_files and not nms_files:
                shutil.copy2(patch_file, paper_dir / patch_file.name)
            elif not nms_files and not cb_files:
                shutil.copy2(patch_file, paper_dir / patch_file.name)
            else:
                subject_lines = [l for l in content.splitlines() if l.startswith("Subject: ")]
                subject = subject_lines[0].lower() if subject_lines else ""
                if "config" in subject or "toml" in subject or "branding" in subject:
                    shutil.copy2(patch_file, paper_dir / patch_file.name)
                elif "backport" in subject or "paper" in subject:
                    shutil.copy2(patch_file, paper_dir / patch_file.name)
                elif len(nms_files) >= len(cb_files):
                    shutil.copy2(patch_file, mc_dir / patch_file.name)
                else:
                    shutil.copy2(patch_file, paper_dir / patch_file.name)

        for p in sorted(tmp_api.glob("*.patch")):
            sort_and_copy(p, api_mc_patches, api_paper_patches)

        for p in sorted(tmp_server.glob("*.patch")):
            sort_and_copy(p, server_mc_patches, server_paper_patches)

    print("Patches rebuilt successfully.")

if __name__ == "__main__":
    if len(sys.argv) < 2 or sys.argv[1] not in ("apply", "rebuild"):
        sys.exit("Usage: patch_manager.py apply | rebuild")
    if sys.argv[1] == "apply":
        apply()
    else:
        rebuild()

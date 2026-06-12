#!/usr/bin/env python3
"""API Deprecation Monitor CLI for Cursor Automations."""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import re
import subprocess
import sys
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

try:
    import yaml
except ImportError:
    subprocess.check_call([sys.executable, "-m", "pip", "install", "-q", "pyyaml"])
    import yaml


def env(name: str, default: str | None = None) -> str:
    val = os.environ.get(name, default)
    if val is None:
        raise RuntimeError(f"Missing required environment variable: {name}")
    return val


def repo_root() -> Path:
    root = os.environ.get("API_MONITOR_REPO_ROOT")
    if root:
        return Path(root).resolve()
    return Path(
        subprocess.check_output(
            ["git", "rev-parse", "--show-toplevel"], text=True
        ).strip()
    )


def workspace_root() -> Path:
    ws = os.environ.get("API_MONITOR_WORKSPACE")
    if ws:
        return Path(ws).resolve()
    return repo_root().parent


def config_path() -> Path:
    cfg = os.environ.get(
        "API_MONITOR_CONFIG", ".github/api-deprecation-monitor/config.yaml"
    )
    return repo_root() / cfg


def catalog_dir() -> Path:
    return repo_root() / "api-deprecation-monitor" / "api-catalog"


def cache_dir() -> Path:
    return repo_root() / "api-deprecation-monitor" / "api-cache"


def reports_dir() -> Path:
    return repo_root() / "api-deprecation-monitor" / "api-reports"


def load_config() -> dict[str, Any]:
    with open(config_path(), encoding="utf-8") as f:
        return yaml.safe_load(f)


def repo_abs_path(repo_cfg: dict[str, Any]) -> Path:
    directory = repo_cfg.get("directory", ".")
    if directory == ".":
        return repo_root()
    return workspace_root() / directory


def git_cmd(cwd: Path, *args: str) -> str:
    return subprocess.check_output(
        ["git", "-C", str(cwd), *args], text=True, stderr=subprocess.STDOUT
    ).strip()


def git_status_dirty(cwd: Path) -> bool:
    out = git_cmd(cwd, "status", "--porcelain")
    return bool(out)


def git_current_branch(cwd: Path) -> str:
    return git_cmd(cwd, "rev-parse", "--abbrev-ref", "HEAD")


def git_head_commit(cwd: Path) -> str:
    return git_cmd(cwd, "rev-parse", "HEAD")


def git_changed_files_since(cwd: Path, since: str | None) -> list[str]:
    if not since:
        return []
    try:
        out = git_cmd(cwd, "diff", "--name-only", since, "HEAD")
        return [line for line in out.splitlines() if line]
    except subprocess.CalledProcessError:
        return []


def cmd_sync_workspace(_args: argparse.Namespace) -> int:
    cfg = load_config()
    ws = workspace_root()
    ws.mkdir(parents=True, exist_ok=True)
    for repo in cfg.get("repos", []):
        directory = repo.get("directory", ".")
        if directory == ".":
            continue
        remote = repo.get("github_remote")
        if not remote:
            print(f"WARN: no github_remote for {repo.get('name')}", file=sys.stderr)
            continue
        dest = ws / directory
        url = f"https://github.com/{remote}.git"
        if dest.exists():
            subprocess.run(
                ["git", "-C", str(dest), "fetch", "origin"],
                check=False,
            )
            subprocess.run(
                ["git", "-C", str(dest), "checkout", "master"],
                check=False,
            )
            subprocess.run(
                ["git", "-C", str(dest), "pull", "origin", "master"],
                check=False,
            )
        else:
            subprocess.run(
                ["git", "clone", url, str(dest)],
                check=False,
            )
    return 0


def cmd_prepare(args: argparse.Namespace) -> int:
    cfg = load_config()
    catalog_file = catalog_dir() / "catalog.yaml"
    existing_catalog: dict[str, Any] = {}
    if catalog_file.exists():
        with open(catalog_file, encoding="utf-8") as f:
            existing_catalog = yaml.safe_load(f) or {}

    repo_names_filter: set[str] | None = None
    if args.repos:
        repo_names_filter = {n.strip() for n in args.repos.split(",") if n.strip()}

    allow_dirty: set[str] = set()
    if args.allow_dirty:
        allow_dirty = {n.strip() for n in args.allow_dirty.split(",") if n.strip()}

    switch_branch = args.switch_branch
    keep_branch: set[str] = set()
    if args.keep_branch:
        keep_branch = {n.strip() for n in args.keep_branch.split(",") if n.strip()}

    dirty_repos: list[str] = []
    non_master_repos: list[dict[str, str]] = []

    delta_repos: list[dict[str, Any]] = []
    skipped_repos: list[dict[str, str]] = []

    for repo_cfg in cfg.get("repos", []):
        name = repo_cfg["name"]
        if repo_names_filter and name not in repo_names_filter:
            continue

        path = repo_abs_path(repo_cfg)
        if not path.exists():
            skipped_repos.append({"name": name, "reason": "path not found"})
            continue

        branch = git_current_branch(path)
        if branch != "master" and name not in keep_branch:
            if switch_branch and name in {switch_branch}:
                subprocess.run(
                    ["git", "-C", str(path), "checkout", "master"],
                    check=True,
                )
                branch = "master"
            elif name not in keep_branch:
                non_master_repos.append({"name": name, "branch": branch})

        if git_status_dirty(path) and name not in allow_dirty:
            dirty_repos.append(name)

    if args.check_only:
        summary = _catalog_summary(existing_catalog)
        print(json.dumps({"catalog_summary": summary}))
        return 0

    if dirty_repos and not allow_dirty:
        print(
            json.dumps(
                {
                    "needs_dirty_allow": True,
                    "dirty_repos": dirty_repos,
                    "non_master_repos": non_master_repos,
                }
            ),
            file=sys.stderr,
        )
        return 3

    if non_master_repos and not switch_branch and not keep_branch:
        print(
            json.dumps(
                {
                    "needs_input": True,
                    "non_master_repos": non_master_repos,
                    "dirty_repos": dirty_repos,
                }
            ),
            file=sys.stderr,
        )
        return 2

    for repo_cfg in cfg.get("repos", []):
        name = repo_cfg["name"]
        if repo_names_filter and name not in repo_names_filter:
            continue
        path = repo_abs_path(repo_cfg)
        if not path.exists():
            continue
        if name in dirty_repos and name not in allow_dirty:
            skipped_repos.append({"name": name, "reason": "dirty worktree"})
            continue
        if any(r["name"] == name for r in non_master_repos) and name not in keep_branch:
            if not (switch_branch and name == switch_branch):
                skipped_repos.append({"name": name, "reason": "non-master branch"})
                continue

        head = git_head_commit(path)
        last_scan = None
        for r in existing_catalog.get("repos", []):
            if r.get("name") == name:
                last_scan = r.get("last_scan_commit")
                break

        scan_mode = "full"
        changed_files: list[str] | str = "all"
        if not args.full_scan and last_scan:
            changed = git_changed_files_since(path, last_scan)
            if changed:
                scan_mode = "delta"
                changed_files = changed
            else:
                scan_mode = "skip"
                changed_files = []

        scan_paths = repo_cfg.get("scan_paths")
        if scan_paths and scan_mode == "delta" and isinstance(changed_files, list):
            changed_files = [
                f
                for f in changed_files
                if any(f.startswith(sp) for sp in scan_paths)
            ]
            if not changed_files:
                scan_mode = "skip"

        delta_repos.append(
            {
                "name": name,
                "path": str(path),
                "head_commit": head,
                "scan_mode": scan_mode,
                "changed_files": changed_files,
            }
        )

    result = {
        "catalog_summary": _catalog_summary(existing_catalog),
        "preflight": {
            "config": str(config_path()),
            "repos_configured": len(cfg.get("repos", [])),
            "skipped": skipped_repos,
        },
        "delta": {"repos": delta_repos},
    }
    print(json.dumps(result))
    return 0


def _catalog_summary(catalog: dict[str, Any]) -> dict[str, Any]:
    repos = catalog.get("repos", [])
    endpoints = 0
    platforms: dict[str, int] = {}
    for repo in repos:
        for ep in repo.get("endpoints", []):
            endpoints += 1
            plat = ep.get("platform", "unknown")
            platforms[plat] = platforms.get(plat, 0) + 1
    return {
        "total_endpoints": endpoints,
        "platforms": platforms,
        "repos_tracked": len(repos),
        "last_updated": catalog.get("last_updated"),
    }


def _parse_max_age(max_age: str) -> int:
    m = re.match(r"^(\d+)(h|d)$", max_age.strip())
    if not m:
        return 86400
    n, unit = int(m.group(1)), m.group(2)
    return n * 3600 if unit == "h" else n * 86400


def cmd_fetch_sources(args: argparse.Namespace) -> int:
    cfg = load_config()
    sources = cfg.get("sources", {})
    src_cache = cache_dir() / "sources"
    meta_file = cache_dir() / "sources_meta.json"
    src_cache.mkdir(parents=True, exist_ok=True)

    meta: dict[str, Any] = {}
    if meta_file.exists():
        with open(meta_file, encoding="utf-8") as f:
            meta = json.load(f)

    max_age = _parse_max_age(args.max_age)
    now = datetime.now(timezone.utc)
    changed: list[str] = []
    unchanged: list[str] = []
    failed: list[str] = []
    platforms_to_check: set[str] = set()

    import urllib.request

    for key, src in sources.items():
        url = src["url"]
        cache_path = src_cache / f"{key}.md"
        try:
            req = urllib.request.Request(
                url, headers={"User-Agent": "api-deprecation-monitor/1.0"}
            )
            with urllib.request.urlopen(req, timeout=60) as resp:
                body = resp.read()
            digest = hashlib.sha256(body).hexdigest()
            prev = meta.get(key, {})
            fetched_at = prev.get("fetched_at")
            stale = True
            if fetched_at:
                prev_dt = datetime.fromisoformat(fetched_at.replace("Z", "+00:00"))
                age = (now - prev_dt).total_seconds()
                stale = age > max_age
            content_changed = prev.get("sha256") != digest
            if stale or content_changed or not cache_path.exists():
                cache_path.write_bytes(body)
                meta[key] = {
                    "url": url,
                    "sha256": digest,
                    "fetched_at": now.isoformat(),
                }
                changed.append(key)
                for p in src.get("platforms", []):
                    platforms_to_check.add(p)
            else:
                unchanged.append(key)
        except Exception as e:
            failed.append(key)
            print(f"WARN: fetch failed for {key}: {e}", file=sys.stderr)

    with open(meta_file, "w", encoding="utf-8") as f:
        json.dump(meta, f, indent=2)

    catalog_file = catalog_dir() / "catalog.yaml"
    if catalog_file.exists():
        with open(catalog_file, encoding="utf-8") as f:
            cat = yaml.safe_load(f) or {}
        last_updated = cat.get("last_updated")
        if last_updated:
            dep_file = catalog_dir() / "deprecations.yaml"
            if dep_file.exists():
                with open(dep_file, encoding="utf-8") as f:
                    dep = yaml.safe_load(f) or {}
                last_checked = dep.get("last_checked")
                if last_checked and last_updated <= last_checked and not changed:
                    platforms_to_check = set()

    print(
        json.dumps(
            {
                "sources_checked": len(sources),
                "changed": changed,
                "unchanged": unchanged,
                "failed": failed,
                "platforms_to_check": sorted(platforms_to_check),
            }
        )
    )
    return 0


def cmd_merge_catalog(args: argparse.Namespace) -> int:
    results_path = Path(args.results_file)
    docs = list(yaml.safe_load_all(results_path.read_text(encoding="utf-8")))
    merged_repos: dict[str, dict[str, Any]] = {}

    catalog_file = catalog_dir() / "catalog.yaml"
    catalog_dir().mkdir(parents=True, exist_ok=True)
    existing: dict[str, Any] = {"repos": []}
    if catalog_file.exists():
        with open(catalog_file, encoding="utf-8") as f:
            existing = yaml.safe_load(f) or {"repos": []}

    for doc in docs:
        if not doc:
            continue
        if "repos" in doc and "repo" not in doc:
            for r in doc["repos"]:
                merged_repos[r["repo"]] = r
        elif "repo" in doc:
            merged_repos[doc["repo"]] = doc

    now = datetime.now(timezone.utc).isoformat()
    out_repos: list[dict[str, Any]] = []
    platforms: dict[str, int] = {}
    total = 0

    for name, repo_data in merged_repos.items():
        endpoints = repo_data.get("endpoints", [])
        libraries = repo_data.get("libraries", [])
        for ep in endpoints:
            total += 1
            plat = ep.get("platform", "unknown")
            platforms[plat] = platforms.get(plat, 0) + 1
        out_repos.append(
            {
                "name": name,
                "last_scan_commit": repo_data.get("head_commit"),
                "last_scan_at": now,
                "scan_mode": repo_data.get("scan_mode"),
                "endpoints": endpoints,
                "libraries": libraries,
            }
        )

    for old in existing.get("repos", []):
        if old["name"] not in merged_repos:
            out_repos.append(old)
            for ep in old.get("endpoints", []):
                total += 1
                plat = ep.get("platform", "unknown")
                platforms[plat] = platforms.get(plat, 0) + 1

    catalog = {
        "last_updated": now,
        "repos": out_repos,
    }
    with open(catalog_file, "w", encoding="utf-8") as f:
        yaml.dump(catalog, f, default_flow_style=False, sort_keys=False, allow_unicode=True)

    print(json.dumps({"total_endpoints": total, "platforms": platforms}))
    return 0


def cmd_merge_verifications(args: argparse.Namespace) -> int:
    dep_file = catalog_dir() / "deprecations.yaml"
    if not dep_file.exists():
        print(json.dumps({"verified": 0, "resolved": 0, "narrowed": 0, "confirmed": 0}))
        return 0

    with open(dep_file, encoding="utf-8") as f:
        deprecations_doc = yaml.safe_load(f) or {}

    verdicts = list(yaml.safe_load_all(Path(args.verdicts_file).read_text(encoding="utf-8")))
    by_id: dict[str, dict[str, Any]] = {}
    for v in verdicts:
        if v and "id" in v:
            by_id[v["id"]] = v

    resolved = narrowed = confirmed = 0
    now = datetime.now(timezone.utc).isoformat()

    for dep in deprecations_doc.get("deprecations", []):
        vid = dep.get("id")
        if vid not in by_id:
            continue
        verdict = by_id[vid]
        dep["verified_at"] = now
        dep["verification"] = {
            "verdict": verdict.get("verdict"),
            "evidence": verdict.get("evidence", []),
        }
        v = verdict.get("verdict")
        if v == "false_positive":
            dep["status"] = "false_positive"
            dep["affected_repos"] = []
            resolved += 1
        elif v == "partial":
            dep["affected_repos"] = verdict.get("affected_repos", dep.get("affected_repos", []))
            narrowed += 1
        elif v == "confirmed":
            confirmed += 1

    with open(dep_file, "w", encoding="utf-8") as f:
        yaml.dump(
            deprecations_doc,
            f,
            default_flow_style=False,
            sort_keys=False,
            allow_unicode=True,
        )

    print(
        json.dumps(
            {
                "verified": len(by_id),
                "resolved": resolved,
                "narrowed": narrowed,
                "confirmed": confirmed,
            }
        )
    )
    return 0


def main() -> int:
    parser = argparse.ArgumentParser(description="API Deprecation Monitor")
    sub = parser.add_subparsers(dest="command", required=True)

    p_sync = sub.add_parser("sync-workspace")
    p_sync.set_defaults(func=cmd_sync_workspace)

    p_prep = sub.add_parser("prepare")
    p_prep.add_argument("--repos")
    p_prep.add_argument("--full-scan", action="store_true")
    p_prep.add_argument("--check-only", action="store_true")
    p_prep.add_argument("--allow-dirty")
    p_prep.add_argument("--switch-branch")
    p_prep.add_argument("--keep-branch")
    p_prep.set_defaults(func=cmd_prepare)

    p_fetch = sub.add_parser("fetch-sources")
    p_fetch.add_argument("--max-age", default="24h")
    p_fetch.set_defaults(func=cmd_fetch_sources)

    p_merge = sub.add_parser("merge-catalog")
    p_merge.add_argument("--results-file", required=True)
    p_merge.set_defaults(func=cmd_merge_catalog)

    p_ver = sub.add_parser("merge-verifications")
    p_ver.add_argument("--verdicts-file", required=True)
    p_ver.set_defaults(func=cmd_merge_verifications)

    args = parser.parse_args()
    try:
        return args.func(args)
    except subprocess.CalledProcessError as e:
        print(e.output, file=sys.stderr)
        return 1
    except Exception as e:
        print(str(e), file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.exit(main())

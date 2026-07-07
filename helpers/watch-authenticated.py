import os
import re
import time
import urllib.request
import json

repos = ["mkdocs-kit", "nunki", "quasar"]

def get_token():
    # Try GITHUB_TOKEN environment variable first
    token = os.environ.get("GITHUB_TOKEN")
    if token:
        return token
    # Try reading from ~/.git-credentials
    cred_path = os.path.expanduser("~/.git-credentials")
    if os.path.exists(cred_path):
        try:
            with open(cred_path, "r") as f:
                content = f.read()
                # Match github token in credentials URL
                match = re.search(r"github\.com.*?:(gho_[a-zA-Z0-9_]+)@", content)
                if match:
                    return match.group(1)
                match = re.search(r":(gho_[a-zA-Z0-9_]+)@", content)
                if match:
                    return match.group(1)
        except Exception:
            pass
    return ""

token = get_token()

def get_run(repo, run_id=None):
    try:
        if run_id:
            url = f"https://api.github.com/repos/mlecabellec/{repo}/actions/runs/{run_id}"
        else:
            url = f"https://api.github.com/repos/mlecabellec/{repo}/actions/runs?per_page=1"
            
        req = urllib.request.Request(
            url, 
            headers={
                'User-Agent': 'Mozilla/5.0',
                'Authorization': f'Bearer {token}',
                'Accept': 'application/vnd.github+json'
            }
        )
        with urllib.request.urlopen(req) as response:
            data = json.loads(response.read().decode())
            if run_id:
                return data
            else:
                runs = data.get("workflow_runs", [])
                return runs[0] if runs else None
    except Exception as e:
        print(f"Error fetching {repo}: {e}")
    return None

def watch():
    print("Watching GitHub Actions Workflow and Pages deployment for mkdocs-kit, nunki, and quasar...")
    
    # Store targets
    targets = {}
    for repo in repos:
        run = get_run(repo)
        if run:
            targets[repo] = {
                "id": run.get("id"),
                "name": run.get("name"),
                "status": run.get("status"),
                "conclusion": run.get("conclusion")
            }
            print(f"Tracking {repo}: Run ID={run.get('id')}, name='{run.get('name')}', status={run.get('status')}")
            
    max_attempts = 30
    for attempt in range(max_attempts):
        all_done = True
        print(f"\n--- Poll Attempt #{attempt + 1} ---")
        
        for repo in repos:
            info = targets[repo]
            run = get_run(repo, info["id"])
            if run:
                status = run.get("status")
                conclusion = run.get("conclusion")
                info["status"] = status
                info["conclusion"] = conclusion
                
                print(f"{repo} Build: ID={info['id']}, Status={status}, Conclusion={conclusion}")
                
                if status in ["queued", "in_progress"]:
                    all_done = False
                elif conclusion != "success":
                    print(f"  {repo} build did NOT succeed: {conclusion}")
                else:
                    # Build succeeded! Let's check the pages-build-deployment workflow status
                    try:
                        url = f"https://api.github.com/repos/mlecabellec/{repo}/actions/runs?per_page=5"
                        req = urllib.request.Request(
                            url, 
                            headers={
                                'User-Agent': 'Mozilla/5.0',
                                'Authorization': f'Bearer {token}',
                                'Accept': 'application/vnd.github+json'
                            }
                        )
                        with urllib.request.urlopen(req) as resp:
                            data = json.loads(resp.read().decode())
                            page_runs = [r for r in data.get("workflow_runs", []) if r.get("name") == "pages-build-deployment"]
                            if page_runs:
                                latest_page_run = page_runs[0]
                                p_status = latest_page_run.get("status")
                                p_conclusion = latest_page_run.get("conclusion")
                                print(f"  {repo} Pages Deploy: Status={p_status}, Conclusion={p_conclusion}")
                                if p_status in ["queued", "in_progress"]:
                                    all_done = False
                            else:
                                print(f"  {repo} Pages Deploy: Waiting for deployment job to trigger...")
                                all_done = False
                    except Exception as e:
                        print(f"  Error checking Pages Deploy for {repo}: {e}")
                        all_done = False
            else:
                all_done = False
                
        if all_done:
            print("\nAll builds and Pages deployments completed successfully!")
            return True
            
        time.sleep(20)
        
    print("\nTimeout reached. Some deployments are still in progress.")
    return False

if __name__ == '__main__':
    watch()

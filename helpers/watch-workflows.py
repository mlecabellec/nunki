import time
import urllib.request
import json

repos = ["mkdocs-kit", "nunki", "quasar"]
api_template = "https://api.github.com/repos/mlecabellec/{}/actions/runs"

def get_latest_run(repo):
    try:
        url = api_template.format(repo)
        req = urllib.request.Request(
            url, 
            headers={'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64)'}
        )
        with urllib.request.urlopen(req) as response:
            data = json.loads(response.read().decode())
            runs = data.get("workflow_runs", [])
            if runs:
                return runs[0]
    except Exception as e:
        print(f"Error fetching {repo}: {e}")
    return None

def watch():
    print("Watching GitHub Workflow execution for mkdocs-kit, nunki, and quasar...")
    
    # Store initial run IDs so we track the ones that are currently running
    initial_runs = {}
    for repo in repos:
        run = get_latest_run(repo)
        if run:
            initial_runs[repo] = {
                "id": run.get("id"),
                "status": run.get("status"),
                "conclusion": run.get("conclusion"),
                "html_url": run.get("html_url")
            }
            print(f"Initial run for {repo}: ID={run.get('id')}, status={run.get('status')}")
        else:
            print(f"No run found for {repo}")
            
    max_attempts = 15
    for attempt in range(max_attempts):
        all_completed = True
        print(f"\n--- Check #{attempt + 1} ---")
        
        for repo in repos:
            run = get_latest_run(repo)
            if run:
                status = run.get("status")
                conclusion = run.get("conclusion")
                run_id = run.get("id")
                
                print(f"{repo}: ID={run_id}, Status={status}, Conclusion={conclusion}")
                
                if status in ["queued", "in_progress"]:
                    all_completed = False
            else:
                print(f"{repo}: Error or no runs found.")
                
        if all_completed:
            print("\nAll workflow runs have completed!")
            return True
            
        print("Sleeping for 20 seconds...")
        time.sleep(20)
        
    print("\nTimeout reached. Some workflows are still in progress.")
    return False

if __name__ == '__main__':
    watch()

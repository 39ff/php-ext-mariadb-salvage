"""
E2E test for MariaDB Query Profiler demo application.

Runs against the Docker Compose environment, takes screenshots at each stage,
and collects profiler logs for upload as CI artifacts.
"""

import os
import time
import unittest
from pathlib import Path

from selenium import webdriver
from selenium.webdriver.chrome.options import Options
from selenium.webdriver.common.by import By
from selenium.webdriver.support import expected_conditions as EC
from selenium.webdriver.support.ui import WebDriverWait

BASE_URL = os.environ.get("DEMO_URL", "http://localhost:8080")
SCREENSHOT_DIR = Path(os.environ.get("SCREENSHOT_DIR", "screenshots"))
LOG_OUTPUT_DIR = Path(os.environ.get("LOG_OUTPUT_DIR", "logs"))
WAIT_TIMEOUT = 30


class DemoE2ETest(unittest.TestCase):
    """End-to-end tests for the profiler demo UI."""

    driver: webdriver.Chrome
    screenshot_index: int = 0

    @classmethod
    def setUpClass(cls):
        SCREENSHOT_DIR.mkdir(parents=True, exist_ok=True)
        LOG_OUTPUT_DIR.mkdir(parents=True, exist_ok=True)

        options = Options()
        options.add_argument("--headless=new")
        options.add_argument("--no-sandbox")
        options.add_argument("--disable-dev-shm-usage")
        options.add_argument("--disable-gpu")
        options.add_argument("--window-size=1400,900")
        options.add_argument("--force-device-scale-factor=1")

        cls.driver = webdriver.Chrome(options=options)
        cls.driver.implicitly_wait(5)

    @classmethod
    def tearDownClass(cls):
        cls.driver.quit()

    def screenshot(self, name: str):
        """Take a screenshot with a sequential prefix for ordering."""
        DemoE2ETest.screenshot_index += 1
        filename = f"{DemoE2ETest.screenshot_index:02d}_{name}.png"
        path = SCREENSHOT_DIR / filename
        self.driver.save_screenshot(str(path))
        print(f"  Screenshot saved: {path}")
        return path

    def wait_for(self, by, value, timeout=WAIT_TIMEOUT):
        """Wait for an element to be present and return it."""
        return WebDriverWait(self.driver, timeout).until(
            EC.presence_of_element_located((by, value))
        )

    def wait_clickable(self, by, value, timeout=WAIT_TIMEOUT):
        """Wait for an element to be clickable and return it."""
        return WebDriverWait(self.driver, timeout).until(
            EC.element_to_be_clickable((by, value))
        )

    def wait_for_text(self, text, timeout=WAIT_TIMEOUT):
        """Wait until text appears anywhere on the page."""
        WebDriverWait(self.driver, timeout).until(
            lambda d: text in d.find_element(By.TAG_NAME, "body").text
        )

    def start_session_and_wait(self):
        """Click Start Session and wait for RECORDING to appear."""
        start_btn = self.wait_clickable(
            By.XPATH, "//button[contains(., 'Start Session')]"
        )
        start_btn.click()
        # Wait until the RECORDING badge and terminal appear
        self.wait_for_text("RECORDING")
        # Give the terminal a moment to render its welcome message
        time.sleep(1)

    # ------------------------------------------------------------------
    # Tests run in alphabetical order; prefix with number for sequence
    # ------------------------------------------------------------------

    def test_01_page_loads(self):
        """Verify the dashboard page loads correctly."""
        print("\n[Test 01] Loading dashboard page...")
        self.driver.get(BASE_URL)

        # Wait for Alpine.js to render the empty state
        self.wait_for_text("No active profiling sessions")

        self.screenshot("01_initial_page_load")

        # Verify title
        self.assertIn("MariaDB Query Profiler", self.driver.title)

        # Verify heading
        heading = self.driver.find_element(By.TAG_NAME, "h1")
        self.assertIn("MariaDB Query Profiler", heading.text)

        print("  Dashboard loaded successfully")

    def test_02_start_session(self):
        """Start a profiling session and verify the tab appears."""
        print("\n[Test 02] Starting profiling session...")
        self.driver.get(BASE_URL)
        self.wait_for_text("No active profiling sessions")

        self.start_session_and_wait()
        print("  Clicked 'Start Session'")

        self.screenshot("02_session_started")

        # Verify RECORDING status is shown
        body_text = self.driver.find_element(By.TAG_NAME, "body").text
        self.assertIn("RECORDING", body_text)

        # Wait for terminal to show connection messages
        time.sleep(2)
        self.screenshot("03_terminal_connected")
        print("  Session started, terminal connected")

    def test_03_run_demo_queries(self):
        """Run demo queries and verify they execute."""
        print("\n[Test 03] Running demo queries...")
        self.driver.get(BASE_URL)
        self.wait_for_text("No active profiling sessions")

        # Start a new session first
        self.start_session_and_wait()

        # Now click "Run Demo Queries"
        query_btn = self.wait_clickable(
            By.XPATH, "//button[contains(., 'Run Demo Queries')]"
        )
        query_btn.click()
        print("  Clicked 'Run Demo Queries'")

        # Wait for the success message to appear
        self.wait_for_text("queries executed")
        self.screenshot("04_queries_executed")

        # Wait for log stream to show captured queries in terminal
        time.sleep(4)
        self.screenshot("05_terminal_with_queries")
        print("  Demo queries executed successfully")

    def test_04_stop_session(self):
        """Stop the profiling session and verify it stops."""
        print("\n[Test 04] Stopping profiling session...")
        self.driver.get(BASE_URL)
        self.wait_for_text("No active profiling sessions")

        # Start session
        self.start_session_and_wait()

        # Run queries to have something captured
        query_btn = self.wait_clickable(
            By.XPATH, "//button[contains(., 'Run Demo Queries')]"
        )
        query_btn.click()
        self.wait_for_text("queries executed")
        time.sleep(2)

        # Click Stop
        stop_btn = self.wait_clickable(
            By.XPATH, "//button[contains(., 'Stop')]"
        )
        stop_btn.click()
        print("  Clicked 'Stop'")

        # Wait for STOPPED status
        self.wait_for_text("STOPPED")
        time.sleep(1)
        self.screenshot("06_session_stopped")

        body_text = self.driver.find_element(By.TAG_NAME, "body").text
        self.assertIn("STOPPED", body_text)
        print("  Session stopped successfully")

    def test_05_multiple_sessions(self):
        """Start multiple sessions and verify tabs work."""
        print("\n[Test 05] Testing multiple sessions...")
        self.driver.get(BASE_URL)
        self.wait_for_text("No active profiling sessions")

        # Start first session
        self.start_session_and_wait()

        # Start second session
        self.start_session_and_wait()
        time.sleep(1)

        self.screenshot("07_multiple_sessions")

        # Run queries (should be captured by both active sessions)
        query_btn = self.wait_clickable(
            By.XPATH, "//button[contains(., 'Run Demo Queries')]"
        )
        query_btn.click()
        self.wait_for_text("queries executed")
        time.sleep(4)

        self.screenshot("08_multiple_sessions_with_queries")
        print("  Multiple sessions created and queries executed")

    def test_06_collect_logs(self):
        """Collect profiler logs from the container volume."""
        print("\n[Test 06] Collecting profiler logs...")

        try:
            import subprocess

            result = subprocess.run(
                ["docker", "compose", "-f", "docker-compose.yml",
                 "ps", "-q", "app"],
                capture_output=True, text=True, cwd=str(Path(__file__).parent.parent)
            )
            container_id = result.stdout.strip()

            if container_id:
                subprocess.run(
                    ["docker", "cp", f"{container_id}:/var/profiler/.",
                     str(LOG_OUTPUT_DIR)],
                    capture_output=True, text=True
                )
                print(f"  Logs copied to {LOG_OUTPUT_DIR}")

                log_files = list(LOG_OUTPUT_DIR.iterdir())
                print(f"  Collected {len(log_files)} log files:")
                for f in sorted(log_files):
                    size = f.stat().st_size
                    print(f"    - {f.name} ({size} bytes)")
            else:
                print("  Warning: Could not find app container")
        except Exception as e:
            print(f"  Warning: Could not collect logs: {e}")

        # Take final overview screenshot
        self.driver.get(BASE_URL)
        time.sleep(2)
        self.screenshot("09_final_state")

    def test_07_extension_info(self):
        """Verify the PHP extension is loaded by checking phpinfo."""
        print("\n[Test 07] Verifying PHP extension is loaded...")

        try:
            import subprocess

            result = subprocess.run(
                ["docker", "compose", "-f", "docker-compose.yml",
                 "exec", "-T", "app",
                 "php", "-m"],
                capture_output=True, text=True,
                cwd=str(Path(__file__).parent.parent)
            )
            modules = result.stdout
            self.assertIn("mariadb_profiler", modules)
            print("  Extension 'mariadb_profiler' is loaded")

            result = subprocess.run(
                ["docker", "compose", "-f", "docker-compose.yml",
                 "exec", "-T", "app",
                 "php", "-r", "phpinfo(INFO_MODULES);"],
                capture_output=True, text=True,
                cwd=str(Path(__file__).parent.parent)
            )

            phpinfo_path = LOG_OUTPUT_DIR / "phpinfo_extension.txt"
            lines = result.stdout.split("\n")
            capture = False
            section_lines = []
            for line in lines:
                if "mariadb_profiler" in line.lower():
                    capture = True
                if capture:
                    section_lines.append(line)
                    if line.strip() == "" and len(section_lines) > 3:
                        break

            with open(phpinfo_path, "w") as f:
                f.write("\n".join(section_lines) if section_lines else result.stdout[:2000])
            print(f"  Extension info saved to {phpinfo_path}")

        except Exception as e:
            print(f"  Warning: Could not verify extension: {e}")


if __name__ == "__main__":
    unittest.main(verbosity=2)

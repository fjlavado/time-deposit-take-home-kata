"""Regression checks for the metrics extracted from Gatling 3.16's HTML table."""
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

from run import summarize_report, wait_for_jobs


# Representative report markup: hidden sort keys must not become request names;
# KO, Cnt/s and 95th/99th columns must not be confused with OK or 75th percentile.
REPORT = """
<table><tbody><tr id="ROOT"><td>All Requests</td></tr></tbody></table>
<table><tbody><tr id="req_get-page--1563438791" data-parent="ROOT">
<td class="total col-1"><div><span class="expand-button hidden">&nbsp;</span>
<a><span class="table-cell-tooltip"><span class="ellipsed-name">GET page</span></span></a>
<span class="value" style="display:none;">0</span></div></td>
<td class="value total col-2">1,000</td><td class="value ok col-3">998</td>
<td class="value ko col-4">2</td><td class="value ko col-5">0.2</td>
<td class="value total col-6">49.75</td><td class="value total col-7">1</td>
<td class="value total col-8">8</td><td class="value total col-9">12</td>
<td class="value total col-10">97</td><td class="value total col-11">301</td>
<td class="value total col-12">10,000</td></tr></tbody></table>
"""


class ReportingTest(unittest.TestCase):
    def parse(self, text):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "index.html"
            path.write_text(text)
            return summarize_report(path)

    def test_reports_actual_counts_failures_throughput_and_percentiles(self):
        self.assertEqual(self.parse(REPORT), {"GET page": {
            "requests": 1000, "failures": 2, "observedRps": 49.75,
            "p50Millis": 8, "p95Millis": 97, "p99Millis": 301, "maxMillis": 10000,
        }})

    def test_missing_report_data_cannot_be_reported_as_zero_failures(self):
        with self.assertRaises(ValueError):
            self.parse("<html><body>Generation failed</body></html>")

    def test_changed_column_layout_fails_instead_of_inventing_metrics(self):
        with self.assertRaises(KeyError):
            self.parse(REPORT.replace('class="value total col-10"', 'class="different"'))

    def test_completion_wait_catches_admission_after_a_client_timeout(self):
        clock = [0]
        completed = [{"status": "COMPLETED"}]
        # There is initially no audit, but busy server requests later admit work.
        with patch("run.jobs", side_effect=[[], [], [{"status": "RUNNING"}],
                                            completed, completed, completed, completed]) as read_jobs, \
                patch("run.db", side_effect=[1, 1, 0, 0, 0, 0, 0]), \
                patch("run.time.monotonic", side_effect=lambda: clock[0]), \
                patch("run.time.sleep", side_effect=lambda seconds: clock.__setitem__(0, clock[0] + seconds)):
            self.assertEqual(wait_for_jobs(30), completed)
            self.assertEqual(read_jobs.call_count, 7)

    def test_unsettled_server_work_has_a_bounded_wait(self):
        clock = [0]
        with patch("run.jobs", return_value=[]), patch("run.db", return_value=1), \
                patch("run.time.monotonic", side_effect=lambda: clock[0]), \
                patch("run.time.sleep", side_effect=lambda seconds: clock.__setitem__(0, clock[0] + seconds)):
            with self.assertRaises(TimeoutError):
                wait_for_jobs(4)


if __name__ == "__main__":
    unittest.main()

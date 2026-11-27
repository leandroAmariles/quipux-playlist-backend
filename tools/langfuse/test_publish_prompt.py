import unittest
from unittest import mock

import publish_prompt


class FakeLangfuse:
    def __init__(self):
        self.versions = []

    def production(self, name):
        return self.versions[-1] if self.versions else None

    def create(self, name, text, commit):
        self.versions.append({"version": len(self.versions) + 1, "prompt": text, "commit": commit})
        return self.versions[-1]


class PublishPromptTest(unittest.TestCase):
    def test_creates_a_version_only_when_the_text_changes(self):
        langfuse = FakeLangfuse()

        self.assertEqual(publish_prompt.publish(langfuse, "p", "one", "abc"), (1, True))
        self.assertEqual(publish_prompt.publish(langfuse, "p", "one", "def"), (1, False))
        self.assertEqual(publish_prompt.publish(langfuse, "p", "two", "def"), (2, True))
        self.assertEqual(langfuse.versions[0]["commit"], "abc")

    def test_refuses_uncommitted_changes(self):
        with mock.patch.object(publish_prompt, "git", return_value=" M v2.txt"):
            with self.assertRaises(SystemExit):
                publish_prompt.commit_of("v2.txt")

    def test_reads_the_text_as_the_backend_does(self):
        text = publish_prompt.text_of("infrastructure/src/main/resources/prompts/recommendations/v2.txt")

        self.assertTrue(text.endswith("<candidatas>{{candidatas}}</candidatas>"))
        self.assertNotIn("\r", text)


if __name__ == "__main__":
    unittest.main()

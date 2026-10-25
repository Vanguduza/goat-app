#!/usr/bin/env python3
from pathlib import Path
import unittest
import verify_html_style_adoption as guard

class HtmlStyleGuardTests(unittest.TestCase):
    def test_native_aliases_use_the_shared_controls(self):
        self.assertEqual([],guard.violations("import com.farmos.core.design.FarmPrimaryButton as Button\n@Composable fun Form(){ Button(onClick={}){} }"))
    def test_direct_and_qualified_defaults_do_not_bypass_style(self):
        for source in ["import androidx.compose.material3.Button\n@Composable fun Form(){}", "@Composable fun Form(){ androidx.compose.material3.OutlinedTextField(value=\"\") }"]:
            self.assertTrue(guard.violations(source))
    def test_reading_theme_is_allowed_but_declaring_another_is_not(self):
        self.assertEqual([],guard.violations("@Composable fun Head(){ Text(style=MaterialTheme.typography.titleLarge) }"))
        self.assertTrue(guard.violations("@Composable fun Head(){ MaterialTheme(typography=Typography()){} }"))
    def test_actual_source_preserves_reference_and_shared_adoption(self):
        result=guard.audit(guard.ROOT)
        self.assertEqual([],result['issues'])
        self.assertGreater(result['source_files'],100)
        self.assertGreater(result['component_consumer_counts']['FarmOperationalPage'],50)
        self.assertEqual('NOT_GRANTED',result['visual_approval'])

if __name__=='__main__':unittest.main()

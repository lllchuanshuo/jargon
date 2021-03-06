package org.irods.jargon.core.rule;

import java.util.ArrayList;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import junit.framework.Assert;

public class IRODSRuleTest {

	@BeforeClass
	public static void setUpBeforeClass() throws Exception {
	}

	@AfterClass
	public static void tearDownAfterClass() throws Exception {
	}

	@Test
	public void testInstance() throws Exception {
		IRODSRule irodsRule = IRODSRule.instance("x", new ArrayList<IRODSRuleParameter>(),
				new ArrayList<IRODSRuleParameter>(), "yyy");
		Assert.assertNotNull("no return from initializer", irodsRule);
	}

	@Test(expected = IllegalArgumentException.class)
	public void testInstanceNullRuleOrigText() throws Exception {
		IRODSRule.instance(null, new ArrayList<IRODSRuleParameter>(), new ArrayList<IRODSRuleParameter>(), "yyy");
	}

	@Test(expected = IllegalArgumentException.class)
	public void testInstanceBlankRuleOrigText() throws Exception {
		IRODSRule.instance("", new ArrayList<IRODSRuleParameter>(), new ArrayList<IRODSRuleParameter>(), "yyy");
	}

	@Test(expected = IllegalArgumentException.class)
	public void testInstanceNullInputParams() throws Exception {
		IRODSRule.instance("xxxx", null, new ArrayList<IRODSRuleParameter>(), "yyy");
	}

	@Test(expected = IllegalArgumentException.class)
	public void testInstanceNullOutputParams() throws Exception {
		IRODSRule.instance("xxxx", new ArrayList<IRODSRuleParameter>(), null, "yyy");
	}

	@Test(expected = IllegalArgumentException.class)
	public void testInstanceNullRuleBody() throws Exception {
		IRODSRule.instance("xxxx", new ArrayList<IRODSRuleParameter>(), new ArrayList<IRODSRuleParameter>(), null);
	}

	@Test(expected = IllegalArgumentException.class)
	public void testInstanceBlankRuleBody() throws Exception {
		IRODSRule.instance("xxxx", new ArrayList<IRODSRuleParameter>(), new ArrayList<IRODSRuleParameter>(), "");
	}

}

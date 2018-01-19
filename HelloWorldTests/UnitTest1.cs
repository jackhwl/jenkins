using System;
using Microsoft.VisualStudio.TestTools.UnitTesting;
using HelloWorld;

namespace HelloWorldTests
{
    [TestClass]
    public class UnitTest1
    {
        [TestMethod]
        public void TestMethod1()
        {
            Assert.AreEqual("Hello World", Program.CreateMessage());
        }

        //[TestMethod]
        //public void TestMethod2()
        //{
        //    Assert.AreEqual("Hello SeaWorld 2", Program.CreateMessage());
        //}
    }
}

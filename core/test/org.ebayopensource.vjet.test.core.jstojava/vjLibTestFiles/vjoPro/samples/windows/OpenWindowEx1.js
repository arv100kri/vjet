vjo.ctype('vjoPro.samples.windows.OpenWindowEx1') //< public
.needs('vjoPro.dsf.window.utils.VjWindow')
.props({
/**
* @return boolean
* @access public
*
*/
//> public boolean openWin()
openWin:function(){
vjoPro.dsf.window.utils.VjWindow.open("http://www.ebay.com", "testWin", "width=600,height=800,location=yes,menubar=yes,scrollbars=yes,status=no", false, false, 600, 800);
return false;
}

})
.endType();

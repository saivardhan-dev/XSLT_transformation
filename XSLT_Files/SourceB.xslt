<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet version="1.0"
                xmlns:xsl="http://www.w3.org/1999/XSL/Transform">

    <xsl:output method="xml" indent="yes" omit-xml-declaration="yes"/>

    <xsl:template match="/">
        <SourceB>
            <Source><xsl:value-of select="//Source"/></Source>
            <Item><xsl:value-of select="//Item"/></Item>
            <amount><xsl:value-of select="//amount"/></amount>
            <Store><xsl:value-of select="//Store"/></Store>
        </SourceB>
    </xsl:template>

</xsl:stylesheet>

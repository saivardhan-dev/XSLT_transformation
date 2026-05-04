<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet version="1.0"
                xmlns:xsl="http://www.w3.org/1999/XSL/Transform">

    <xsl:output method="xml" indent="yes" omit-xml-declaration="yes"/>

    <xsl:template match="/">
        <SourceA>
            <Source><xsl:value-of select="//Source"/></Source>
            <aItem><xsl:value-of select="//aItem"/></aItem>
            <aPrice><xsl:value-of select="//aPrice"/></aPrice>
            <aLocation><xsl:value-of select="//aLocation"/></aLocation>
            <aStore><xsl:value-of select="//aStore"/></aStore>
        </SourceA>
    </xsl:template>

</xsl:stylesheet>

<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet version="1.0"
                xmlns:xsl="http://www.w3.org/1999/XSL/Transform">

    <xsl:output method="xml" indent="yes" omit-xml-declaration="yes"/>

    <xsl:template match="/">
        <SourceA>
            <Source><xsl:value-of select="//Source"/></Source>
            <cItem><xsl:value-of select="//cItem"/></cItem>
            <cPrice><xsl:value-of select="//cPrice"/></cPrice>
            <cLocation><xsl:value-of select="//cLocation"/></cLocation>
            <cStore><xsl:value-of select="//cStore"/></cStore>
        </SourceA>
    </xsl:template>

</xsl:stylesheet>
